package com.scanvault.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KeyRotationTest {

    private val crypto = VaultCrypto()
    private val rotation = KeyRotation(crypto)

    private fun randomKey(): ByteArray = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }

    @Test
    fun `rewrapKey allows decryption with new key`() {
        val oldKey = randomKey()
        val newKey = randomKey()
        val dataKey = randomKey() // the doc key being wrapped

        // wrap with old key
        val initialBlob = crypto.encrypt(dataKey, oldKey)
        with(rotation) {
            val wrappedBytes = initialBlob.toBytes()

            // rotate
            val rewrapped = rotation.rewrapKey(oldKey, newKey, wrappedBytes)

            // decrypt data with new key after rotation
            val rewrappedBlob = EncryptedBlob(
                nonce = rewrapped.copyOfRange(0, 12),
                ciphertext = rewrapped.copyOfRange(12, rewrapped.size),
            )
            val recoveredDataKey = crypto.decrypt(rewrappedBlob, newKey)
            assertArrayEquals("Data key must survive rotation", dataKey, recoveredDataKey)
        }
    }

    @Test
    fun `encrypt data, rotate key, decrypt with new key returns same plaintext`() {
        val oldKey = randomKey()
        val newKey = randomKey()
        val docKey = randomKey()

        // Encrypt a document with docKey
        val docContent = "Important document text".toByteArray()
        val encryptedDoc = crypto.encrypt(docContent, docKey)

        // Wrap docKey with oldKey
        val wrappedDocKey = with(rotation) { crypto.encrypt(docKey, oldKey).toBytes() }

        // Rotate: rewrap docKey from oldKey -> newKey
        val rewrappedDocKey = rotation.rewrapKey(oldKey, newKey, wrappedDocKey)

        // Unwrap docKey with newKey
        val rewrappedBlob = EncryptedBlob(
            nonce = rewrappedDocKey.copyOfRange(0, 12),
            ciphertext = rewrappedDocKey.copyOfRange(12, rewrappedDocKey.size),
        )
        val recoveredDocKey = crypto.decrypt(rewrappedBlob, newKey)

        // Decrypt the document with recovered docKey
        val decrypted = crypto.decrypt(encryptedDoc, recoveredDocKey)
        assertArrayEquals("Document content must survive key rotation", docContent, decrypted)
    }

    @Test(expected = Exception::class)
    fun `rewrapKey with wrong old key throws`() {
        val oldKey = randomKey()
        val wrongKey = randomKey()
        val newKey = randomKey()
        val dataKey = randomKey()

        val wrappedBytes = with(rotation) { crypto.encrypt(dataKey, oldKey).toBytes() }
        rotation.rewrapKey(wrongKey, newKey, wrappedBytes) // must throw
    }

    @Test
    fun `rewrapped bytes differ from original wrapped bytes`() {
        val oldKey = randomKey()
        val newKey = randomKey()
        val dataKey = randomKey()

        val wrappedBytes = with(rotation) { crypto.encrypt(dataKey, oldKey).toBytes() }
        val rewrapped = rotation.rewrapKey(oldKey, newKey, wrappedBytes)

        assertFalse("Rewrapped bytes must differ from original", wrappedBytes.contentEquals(rewrapped))
    }
}
