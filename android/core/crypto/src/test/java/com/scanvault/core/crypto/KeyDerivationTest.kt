package com.scanvault.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class KeyDerivationTest {

    private val kdf = KeyDerivation()

    @Test
    fun `deriveKey returns 32 bytes`() {
        val key = kdf.deriveKey("password".toCharArray(), ByteArray(16) { it.toByte() })
        assertNotNull(key)
        assert(key.size == 32) { "Expected 32 bytes, got ${key.size}" }
    }

    @Test
    fun `deriveKey produces consistent output for same inputs`() {
        val password = "P@ssw0rd!1".toCharArray()
        val salt = ByteArray(16) { 0xAB.toByte() }
        val k1 = kdf.deriveKey(password, salt)
        val k2 = kdf.deriveKey(password, salt)
        assertArrayEquals("Same inputs must produce same key", k1, k2)
    }

    @Test
    fun `deriveKey produces different output for different passwords`() {
        val salt = ByteArray(16) { 0x01 }
        val k1 = kdf.deriveKey("password1".toCharArray(), salt)
        val k2 = kdf.deriveKey("password2".toCharArray(), salt)
        assertFalse("Different passwords must produce different keys", k1.contentEquals(k2))
    }

    @Test
    fun `deriveKey produces different output for different salts`() {
        val password = "password".toCharArray()
        val k1 = kdf.deriveKey(password, ByteArray(16) { 0x01 })
        val k2 = kdf.deriveKey(password, ByteArray(16) { 0x02 })
        assertFalse("Different salts must produce different keys", k1.contentEquals(k2))
    }

    @Test
    fun `deriveEncryptKey returns 32 bytes`() {
        val master = ByteArray(32) { it.toByte() }
        val encKey = kdf.deriveEncryptKey(master, "encrypt")
        assert(encKey.size == 32) { "Expected 32 bytes, got ${encKey.size}" }
    }

    @Test
    fun `deriveEncryptKey produces different keys for different contexts`() {
        val master = ByteArray(32) { it.toByte() }
        val encKey = kdf.deriveEncryptKey(master, "encrypt")
        val authKey = kdf.deriveEncryptKey(master, "auth")
        assertFalse("Different contexts must yield different keys", encKey.contentEquals(authKey))
    }

    @Test
    fun `deriveEncryptKey is deterministic`() {
        val master = ByteArray(32) { it.toByte() }
        val k1 = kdf.deriveEncryptKey(master, "vault")
        val k2 = kdf.deriveEncryptKey(master, "vault")
        assertArrayEquals(k1, k2)
    }
}
