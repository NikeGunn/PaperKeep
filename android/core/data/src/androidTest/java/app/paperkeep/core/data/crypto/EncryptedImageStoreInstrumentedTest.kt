package app.paperkeep.core.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Instrumented tests for P1.11 — EncryptedImageStore with the real Android Keystore.
 *
 * These must run on a device or emulator (keys live in hardware Keystore, not JVM).
 *
 * Key assertions:
 *  1. Round-trip: write → read → same bytes
 *  2. Raw .enc file is NOT plaintext — an attacker reading the file bytes cannot
 *     recover the original data without the Keystore key.
 *  3. Tampered ciphertext fails decryption (GCM auth tag enforces integrity).
 *  4. A second key cannot decrypt ciphertext produced by the first key.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedImageStoreInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val keyAlias = "Paperkeep_test_key_${System.currentTimeMillis()}"
    private lateinit var store: AesGcmImageStore
    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        tmpDir = File(context.cacheDir, "crypto_test_${System.currentTimeMillis()}").also { it.mkdirs() }
        store = AesGcmImageStore(RealKeyStoreKeyProvider(keyAlias))
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
        // Clean up the test key from Keystore
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
            ks.deleteEntry(keyAlias)
        }
    }

    // ── P1.11: Round-trip with hardware-backed key ────────────────────────────

    @Test
    fun roundTrip_withKeystoreKey_returnsOriginalData() {
        val original = "Paperkeep instrumented test payload".toByteArray()
        val file = File(tmpDir, "rt.enc")

        store.write(file, original)
        val decrypted = store.read(file)

        assertArrayEquals("Round-trip with Keystore key must return identical bytes", original, decrypted)
    }

    @Test
    fun roundTrip_emptyData_withKeystoreKey() {
        val original = ByteArray(0)
        val file = File(tmpDir, "empty.enc")

        store.write(file, original)
        assertArrayEquals(original, store.read(file))
    }

    @Test
    fun roundTrip_binaryData_withAllByteValues() {
        val original = ByteArray(256) { it.toByte() }
        val file = File(tmpDir, "binary.enc")

        store.write(file, original)
        assertArrayEquals(original, store.read(file))
    }

    // ── P1.11: Raw file is NOT plaintext ─────────────────────────────────────

    @Test
    fun rawEncFile_doesNotContainPlaintext() {
        val plaintext = "sensitive-document-data-do-not-expose".toByteArray()
        val file = File(tmpDir, "secret.enc")

        store.write(file, plaintext)

        val rawBytes = file.readBytes()
        // File must be larger than plaintext (IV + GCM tag overhead)
        assertTrue(
            "Encrypted file must be larger than plaintext (has IV + GCM tag)",
            rawBytes.size > plaintext.size,
        )
        // Raw bytes must NOT contain the plaintext as a contiguous subsequence
        assertFalse(
            "Raw .enc file must not contain plaintext bytes",
            containsSubarray(rawBytes, plaintext),
        )
    }

    @Test
    fun rawEncFile_randomIv_differentCiphertextsForSamePlaintext() {
        val data = "same plaintext same key".toByteArray()
        val f1 = File(tmpDir, "iv1.enc")
        val f2 = File(tmpDir, "iv2.enc")

        store.write(f1, data)
        store.write(f2, data)

        assertFalse(
            "Two encryptions of the same plaintext must differ (random IV)",
            f1.readBytes().contentEquals(f2.readBytes()),
        )
    }

    // ── P1.11: Tamper detection ───────────────────────────────────────────────

    @Test(expected = Exception::class)
    fun tamperedCiphertext_throwsOnDecrypt() {
        val file = File(tmpDir, "tampered.enc")
        store.write(file, "original secret".toByteArray())

        // Flip the last byte of the file (part of the GCM auth tag or ciphertext)
        val bytes = file.readBytes().toMutableList()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
        file.writeBytes(bytes.toByteArray())

        store.read(file) // must throw AEADBadTagException
    }

    @Test(expected = Exception::class)
    fun tamperedIv_throwsOnDecrypt() {
        val file = File(tmpDir, "tampered_iv.enc")
        store.write(file, "original secret".toByteArray())

        // Flip a byte in the IV region (first 12 bytes)
        val bytes = file.readBytes().toMutableList()
        bytes[0] = (bytes[0].toInt() xor 0xFF).toByte()
        file.writeBytes(bytes.toByteArray())

        store.read(file) // must throw — IV change fails GCM auth
    }

    // ── P1.11: Wrong key cannot decrypt ──────────────────────────────────────

    @Test(expected = Exception::class)
    fun differentKey_cannotDecryptCiphertext() {
        val file = File(tmpDir, "wrong_key.enc")
        store.write(file, "secret data".toByteArray())

        // Create a second key (different alias → different key material)
        val otherAlias = "${keyAlias}_other"
        val otherStore = AesGcmImageStore(RealKeyStoreKeyProvider(otherAlias))
        try {
            otherStore.read(file) // must throw — wrong key
        } finally {
            runCatching {
                val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
                ks.deleteEntry(otherAlias)
            }
        }
    }

    // ── P1.11: File layout — IV(12) + ciphertext + GCM tag(16) ──────────────

    @Test
    fun fileSize_isIvPlusCiphertextPlusTag() {
        val plaintext = ByteArray(100)
        val file = File(tmpDir, "size.enc")

        store.write(file, plaintext)

        val expected = 12L + 100L + 16L
        assertTrue(
            "File size must be IV(12) + plaintext(100) + tag(16) = $expected, got ${file.length()}",
            file.length() == expected,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun containsSubarray(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }
}

/**
 * KeyProvider that creates / retrieves a real Keystore-backed key.
 * Attempts StrongBox first; falls back to TEE-only on devices without it.
 */
private class RealKeyStoreKeyProvider(private val alias: String) : KeyProvider {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
    }

    override fun getKey(): SecretKey {
        val existing = keyStore.getKey(alias, null) as? SecretKey
        return existing ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val spec = buildSpec(tryStrongBox = true)
        return try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(spec) }.generateKey()
        } catch (e: Exception) {
            // StrongBox unavailable — fall back to TEE
            val fallback = buildSpec(tryStrongBox = false)
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply { init(fallback) }.generateKey()
        }
    }

    private fun buildSpec(tryStrongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(false)
        if (tryStrongBox) {
            try {
                builder.setIsStrongBoxBacked(true)
            } catch (e: Exception) {
                // API level doesn't support StrongBox flag — ignore
            }
        }
        return builder.build()
    }
}
