package app.paperkeep.core.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces (or retrieves) a hardware-backed AES-256 key from Android Keystore.
 *
 * Key properties:
 *  - Algorithm: AES, 256-bit
 *  - Block mode: GCM
 *  - Padding: None
 *  - Authentication: none (the OS enforces it is only accessible to this app's UID)
 *  - User authentication: NOT required (scan encryption is silent/background)
 */
@Singleton
class KeyStoreKeyProvider @Inject constructor() : KeyProvider {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
    }

    override fun getKey(): SecretKey {
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        return existing ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(false) // we supply our own IV
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "Paperkeep_image_key_v1"
    }
}
