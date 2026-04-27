package app.paperkeep.core.backup.format

import app.paperkeep.core.backup.crypto.BackupCipher
import app.paperkeep.core.backup.crypto.BackupKdf
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Wire-level header for a Paperkeep backup file.
 *
 * Layout (big-endian):
 *
 * ```
 *   offset  size  field
 *   ------  ----  -----------------------------------------
 *      0      4   magic     ASCII "PKBK"     (0x50 0x4B 0x42 0x4B)
 *      4      2   version   uint16  current = 1
 *      6      4   memoryKib uint32  Argon2id memory cost
 *     10      4   iterations uint32 Argon2id time cost
 *     14      4   parallelism uint32 Argon2id parallelism
 *     18     32   salt      Argon2id salt
 *     50     12   iv        AES-GCM IV
 *     62      …   ciphertext (auth-tag appended internally)
 * ```
 *
 * Total fixed header size: 62 bytes. Everything after is encrypted.
 *
 * Versioning rule: any wire change must bump [VERSION_CURRENT]; restore must
 * gracefully reject newer files with [BackupHeaderException.UnsupportedVersion]
 * so a future build that reads an older one is forward-compatible by design.
 */
data class BackupHeader(
    val version: Int,
    val memoryKib: Int,
    val iterations: Int,
    val parallelism: Int,
    val salt: ByteArray,
    val iv: ByteArray,
) {

    fun writeTo(out: OutputStream) {
        val dos = DataOutputStream(out)
        dos.write(MAGIC)
        dos.writeShort(version)
        dos.writeInt(memoryKib)
        dos.writeInt(iterations)
        dos.writeInt(parallelism)
        dos.write(salt)
        dos.write(iv)
        dos.flush()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupHeader) return false
        return version == other.version &&
            memoryKib == other.memoryKib &&
            iterations == other.iterations &&
            parallelism == other.parallelism &&
            salt.contentEquals(other.salt) &&
            iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + memoryKib
        result = 31 * result + iterations
        result = 31 * result + parallelism
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }

    companion object {
        val MAGIC: ByteArray = byteArrayOf(0x50, 0x4B, 0x42, 0x4B) // "PKBK"
        const val VERSION_CURRENT: Int = 1
        const val MIN_VERSION_SUPPORTED: Int = 1
        const val FIXED_SIZE: Int = 4 + 2 + 4 + 4 + 4 + BackupKdf.SALT_BYTES + BackupCipher.IV_BYTES

        fun new(
            memoryKib: Int = BackupKdf.MEMORY_KIB,
            iterations: Int = BackupKdf.ITERATIONS,
            parallelism: Int = BackupKdf.PARALLELISM,
            salt: ByteArray,
            iv: ByteArray,
        ): BackupHeader = BackupHeader(
            version = VERSION_CURRENT,
            memoryKib = memoryKib,
            iterations = iterations,
            parallelism = parallelism,
            salt = salt,
            iv = iv,
        )

        /** Read the header from [input]. Throws [BackupHeaderException] on any malformed input. */
        fun readFrom(input: InputStream): BackupHeader {
            val dis = DataInputStream(input)
            val magic = ByteArray(4)
            dis.readFully(magic)
            if (!magic.contentEquals(MAGIC)) {
                throw BackupHeaderException.BadMagic(magic)
            }
            val version = dis.readUnsignedShort()
            if (version < MIN_VERSION_SUPPORTED || version > VERSION_CURRENT) {
                throw BackupHeaderException.UnsupportedVersion(version)
            }
            val memoryKib = dis.readInt()
            val iterations = dis.readInt()
            val parallelism = dis.readInt()
            if (memoryKib <= 0 || iterations <= 0 || parallelism <= 0) {
                throw BackupHeaderException.InvalidParams(memoryKib, iterations, parallelism)
            }
            val salt = ByteArray(BackupKdf.SALT_BYTES).also { dis.readFully(it) }
            val iv = ByteArray(BackupCipher.IV_BYTES).also { dis.readFully(it) }
            return BackupHeader(version, memoryKib, iterations, parallelism, salt, iv)
        }
    }
}

sealed class BackupHeaderException(message: String) : RuntimeException(message) {
    class BadMagic(val actual: ByteArray) : BackupHeaderException(
        "not a Paperkeep backup file (bad magic: ${actual.joinToString { "%02X".format(it) }})"
    )
    class UnsupportedVersion(val version: Int) : BackupHeaderException(
        "unsupported backup version: $version (this build supports up to ${BackupHeader.VERSION_CURRENT})"
    )
    class InvalidParams(val mem: Int, val it: Int, val par: Int) : BackupHeaderException(
        "invalid Argon2id params: m=$mem t=$it p=$par"
    )
}
