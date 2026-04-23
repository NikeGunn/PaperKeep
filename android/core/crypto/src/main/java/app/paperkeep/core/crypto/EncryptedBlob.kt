package app.paperkeep.core.crypto

/**
 * Container for AES-256-GCM encrypted data.
 *
 * @param ciphertext the encrypted bytes (includes GCM auth tag)
 * @param nonce      12-byte random IV used during encryption
 */
data class EncryptedBlob(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedBlob) return false
        return ciphertext.contentEquals(other.ciphertext) && nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + nonce.contentHashCode()
}
