package com.scanvault.core.data.crypto

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * In-process software AES key for unit tests — no Android Keystore required.
 */
class TestKeyProvider : KeyProvider {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun getKey(): SecretKey = key
}
