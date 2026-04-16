package com.scanvault.core.crypto

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Key rotation helpers.
 *
 * The "wrapped key" pattern: K_master wraps K_data (a random 32-byte document key).
 * During rotation the wrapped key is decrypted with the old master, then re-encrypted
 * with the new master — K_data itself never changes, so documents don't need re-encryption.
 */
@Singleton
class KeyRotation @Inject constructor(
    private val vaultCrypto: VaultCrypto,
) {

    /**
     * Re-encrypt [wrappedKey] under [newKey] after decrypting it with [oldKey].
     *
     * @param oldKey    current K_master (32 bytes)
     * @param newKey    replacement K_master (32 bytes)
     * @param wrappedKey serialised [EncryptedBlob] bytes = [nonce (12)] + [ciphertext]
     * @return new wrapped key bytes in the same format
     */
    fun rewrapKey(oldKey: ByteArray, newKey: ByteArray, wrappedKey: ByteArray): ByteArray {
        val blob = wrappedKey.toEncryptedBlob()
        val plainKey = vaultCrypto.decrypt(blob, oldKey)
        val newBlob = vaultCrypto.encrypt(plainKey, newKey)
        return newBlob.toBytes()
    }

    // ---- Serialisation helpers ----

    /** Packs [EncryptedBlob] into [nonce (12 bytes)] + [ciphertext]. */
    fun EncryptedBlob.toBytes(): ByteArray = nonce + ciphertext

    /** Unpacks bytes produced by [toBytes]. */
    private fun ByteArray.toEncryptedBlob(): EncryptedBlob {
        require(size > 12) { "wrapped key too short: $size bytes" }
        return EncryptedBlob(
            nonce = copyOfRange(0, 12),
            ciphertext = copyOfRange(12, size),
        )
    }
}
