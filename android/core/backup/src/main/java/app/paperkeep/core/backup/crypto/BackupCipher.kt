package app.paperkeep.core.backup.crypto

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming AES-256-GCM helper for backup payloads.
 *
 * Both wrappers consume the IV from the caller (we pin the IV in the backup
 * header — see [BackupHeader]) so callers can audit the wire format directly.
 *
 * GCM auth tag size: 128 bits, appended to the last block of ciphertext.
 */
object BackupCipher {

    const val IV_BYTES = 12
    const val TAG_BITS = 128

    private val random = SecureRandom()

    fun newIv(): ByteArray = ByteArray(IV_BYTES).also { random.nextBytes(it) }

    /** Returns an OutputStream that writes ciphertext to [target]. */
    fun encryptingStream(
        key: SecretKeySpec,
        iv: ByteArray,
        target: OutputStream,
    ): CipherOutputStream {
        require(iv.size == IV_BYTES) { "iv must be $IV_BYTES bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        return CipherOutputStream(target, cipher)
    }

    /** Returns an InputStream that decrypts ciphertext from [source]. */
    fun decryptingStream(
        key: SecretKeySpec,
        iv: ByteArray,
        source: InputStream,
    ): CipherInputStream {
        require(iv.size == IV_BYTES) { "iv must be $IV_BYTES bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        return CipherInputStream(source, cipher)
    }
}
