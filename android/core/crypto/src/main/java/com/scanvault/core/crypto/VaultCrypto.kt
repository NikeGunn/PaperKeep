package com.scanvault.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM symmetric encryption for vault documents.
 *
 * Each [encrypt] call generates a fresh 12-byte nonce.  The nonce is
 * stored alongside the ciphertext in [EncryptedBlob] — callers MUST
 * persist it; there is no way to decrypt without it.
 */
@Singleton
class VaultCrypto @Inject constructor() {

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val NONCE_LENGTH_BYTES = 12
    }

    private val random = SecureRandom()

    /**
     * Encrypt [data] with [key] (32 bytes, AES-256).
     *
     * @return [EncryptedBlob] containing ciphertext (with appended GCM tag) and the nonce.
     */
    fun encrypt(data: ByteArray, key: ByteArray): EncryptedBlob {
        require(key.size == 32) { "Key must be 32 bytes for AES-256, got ${key.size}" }
        val nonce = ByteArray(NONCE_LENGTH_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, KEY_ALGORITHM),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce),
        )
        return EncryptedBlob(cipher.doFinal(data), nonce)
    }

    /**
     * Decrypt [blob] with [key].
     *
     * @throws javax.crypto.AEADBadTagException if the key is wrong or data is tampered.
     */
    fun decrypt(blob: EncryptedBlob, key: ByteArray): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes for AES-256, got ${key.size}" }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, KEY_ALGORITHM),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, blob.nonce),
        )
        return cipher.doFinal(blob.ciphertext)
    }
}
