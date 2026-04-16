package com.scanvault.core.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PBKDF2-based key derivation for ScanVault E2E encryption.
 *
 * - [deriveKey] produces a 32-byte master key (K_master) from a password + salt.
 * - [deriveEncryptKey] stretches K_master into a context-specific encryption key (K_encrypt)
 *   using one additional PBKDF2 round with the context bytes as "password".
 */
@Singleton
class KeyDerivation @Inject constructor() {

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val KEY_LENGTH_BITS = 256 // 32 bytes
        const val DEFAULT_ITERATIONS = 100_000
    }

    /**
     * Derive K_master from [password] and [salt].
     *
     * @param password  UTF-8 user password chars
     * @param salt      random 16-byte salt (stored with the account)
     * @param iterations KDF work factor (default 100 000)
     * @return 32-byte master key
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM)
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Derive a context-specific K_encrypt from [masterKey] and a [context] label.
     *
     * Uses one PBKDF2 round with masterKey-as-password and context-as-salt,
     * so different contexts yield independent keys without additional randomness.
     *
     * @param masterKey 32-byte K_master returned by [deriveKey]
     * @param context   ASCII label, e.g. "encrypt" or "auth"
     * @return 32-byte context key
     */
    fun deriveEncryptKey(masterKey: ByteArray, context: String): ByteArray {
        val contextBytes = context.toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(
            masterKey.toCharArray(),
            contextBytes,
            1,
            KEY_LENGTH_BITS,
        )
        return try {
            SecretKeyFactory.getInstance(ALGORITHM)
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    // Converts a ByteArray to a CharArray (byte values as chars) — avoids heap-copy via String.
    private fun ByteArray.toCharArray(): CharArray = CharArray(size) { i -> this[i].toInt().toChar() }
}
