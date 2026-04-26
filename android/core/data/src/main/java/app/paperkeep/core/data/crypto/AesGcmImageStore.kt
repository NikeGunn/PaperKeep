package app.paperkeep.core.data.crypto

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM encrypted image store.
 *
 * File layout:
 *   [IV: 12 bytes][ciphertext + 16-byte GCM auth tag]
 *
 * A fresh random IV is generated per write — reusing IVs with the same key
 * is catastrophic for GCM, so we never do it.
 *
 * The [SecretKey] is provided by [KeyStoreKeyProvider], which hardware-backs it
 * when the device supports StrongBox / TEE. In unit tests a software-only key
 * is injected via [TestKeyProvider].
 */
@Singleton
class AesGcmImageStore @Inject constructor(
    private val keyProvider: KeyProvider,
) : EncryptedImageStore {

    private val random = SecureRandom()

    override fun write(destFile: File, data: ByteArray) {
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = cipher(Cipher.ENCRYPT_MODE, iv)
        val ciphertext = cipher.doFinal(data)

        val parent = destFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            error("Unable to create encrypted image directory: ${parent.absolutePath}")
        }
        destFile.outputStream().use { out ->
            out.write(iv)
            out.write(ciphertext)
        }
    }

    override fun read(srcFile: File): ByteArray {
        val bytes = srcFile.readBytes()
        require(bytes.size >= IV_LEN) { "File too short to contain IV: ${srcFile.name}" }

        val iv = bytes.copyOfRange(0, IV_LEN)
        val ciphertext = bytes.copyOfRange(IV_LEN, bytes.size)
        return cipher(Cipher.DECRYPT_MODE, iv).doFinal(ciphertext)
    }

    override fun delete(file: File) {
        file.delete()
    }

    private fun cipher(mode: Int, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, keyProvider.getKey(), GCMParameterSpec(TAG_BITS, iv))
        }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LEN = 12
        private const val TAG_BITS = 128
    }
}

/** Abstraction over key retrieval so tests can inject a software key. */
interface KeyProvider {
    fun getKey(): SecretKey
}
