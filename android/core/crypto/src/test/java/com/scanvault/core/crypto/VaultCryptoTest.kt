package com.scanvault.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import javax.crypto.AEADBadTagException

class VaultCryptoTest {

    private val crypto = VaultCrypto()

    private fun randomKey(): ByteArray = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }

    @Test
    fun `encrypt returns non-null blob with non-empty ciphertext and nonce`() {
        val key = randomKey()
        val blob = crypto.encrypt("hello".toByteArray(), key)
        assertNotNull(blob)
        assert(blob.nonce.size == 12) { "Nonce must be 12 bytes" }
        assert(blob.ciphertext.isNotEmpty())
    }

    @Test
    fun `encrypt then decrypt returns same plaintext`() {
        val key = randomKey()
        val plaintext = "ScanVault secret document content".toByteArray()
        val blob = crypto.encrypt(plaintext, key)
        val decrypted = crypto.decrypt(blob, key)
        assertArrayEquals("Decrypt must recover plaintext", plaintext, decrypted)
    }

    @Test
    fun `encrypted data is not equal to plaintext`() {
        val key = randomKey()
        val plaintext = "not secret anymore".toByteArray()
        val blob = crypto.encrypt(plaintext, key)
        assertFalse("Ciphertext must not equal plaintext", blob.ciphertext.contentEquals(plaintext))
    }

    @Test
    fun `different keys produce different ciphertext`() {
        val key1 = randomKey()
        val key2 = randomKey()
        val plaintext = "same data".toByteArray()
        val blob1 = crypto.encrypt(plaintext, key1)
        val blob2 = crypto.encrypt(plaintext, key2)
        assertFalse("Different keys must yield different ciphertext", blob1.ciphertext.contentEquals(blob2.ciphertext))
    }

    @Test
    fun `encrypt is non-deterministic — same key + plaintext produce different ciphertexts`() {
        val key = randomKey()
        val plaintext = "nonce test".toByteArray()
        val blob1 = crypto.encrypt(plaintext, key)
        val blob2 = crypto.encrypt(plaintext, key)
        // Nonces should differ (practically certain with SecureRandom)
        assertFalse("Each encrypt call must use a fresh nonce", blob1.nonce.contentEquals(blob2.nonce))
    }

    @Test(expected = Exception::class)
    fun `decrypt with wrong key throws exception`() {
        val key1 = randomKey()
        val key2 = randomKey()
        val blob = crypto.encrypt("secret".toByteArray(), key1)
        crypto.decrypt(blob, key2) // must throw AEADBadTagException or similar
    }

    @Test
    fun `encrypt empty byte array succeeds`() {
        val key = randomKey()
        val blob = crypto.encrypt(ByteArray(0), key)
        val decrypted = crypto.decrypt(blob, key)
        assertArrayEquals(ByteArray(0), decrypted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encrypt with wrong key length throws`() {
        crypto.encrypt("data".toByteArray(), ByteArray(16)) // 16 bytes, not 32
    }
}
