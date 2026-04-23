package app.paperkeep.core.data.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for 1B.15: AES-256-GCM encrypted image store.
 *
 * Uses [TestKeyProvider] (software key) — no Android Keystore required.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AesGcmImageStoreTest {

    private lateinit var store: AesGcmImageStore
    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        tmpDir = createTempDir("Paperkeep_test_")
        store = AesGcmImageStore(TestKeyProvider())
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    // ── 1B.15: Round-trip (encrypt → decrypt → same data) ────────────────────

    @Test
    fun writeRead_roundTrip_returnsOriginalData() {
        val original = "Hello Paperkeep encrypted storage!".toByteArray()
        val file = File(tmpDir, "test.enc")

        store.write(file, original)
        val decrypted = store.read(file)

        assertArrayEquals("Round-trip must return identical bytes", original, decrypted)
    }

    @Test
    fun writeRead_emptyData_roundTrip() {
        val original = ByteArray(0)
        val file = File(tmpDir, "empty.enc")

        store.write(file, original)
        val decrypted = store.read(file)

        assertArrayEquals(original, decrypted)
    }

    @Test
    fun writeRead_largeData_10MB_roundTrip() {
        val original = ByteArray(10 * 1024 * 1024) { it.toByte() }
        val file = File(tmpDir, "large.enc")

        store.write(file, original)
        val decrypted = store.read(file)

        assertArrayEquals(original, decrypted)
    }

    @Test
    fun writeRead_binaryData_withAllByteValues_roundTrip() {
        val original = ByteArray(256) { it.toByte() }
        val file = File(tmpDir, "binary.enc")

        store.write(file, original)
        val decrypted = store.read(file)

        assertArrayEquals(original, decrypted)
    }

    // ── 1B.15: Encrypted file is not plaintext-readable ──────────────────────

    @Test
    fun write_encryptedFile_isNotPlaintext() {
        val plaintext = "sensitive document content".toByteArray()
        val file = File(tmpDir, "secret.enc")

        store.write(file, plaintext)

        val rawBytes = file.readBytes()
        // The file must be longer than the plaintext (IV + auth tag overhead)
        assertTrue(rawBytes.size > plaintext.size)
        // The raw bytes must NOT contain the plaintext as a subsequence
        assertFalse(
            "Encrypted file must not contain plaintext",
            containsSubsequence(rawBytes, plaintext),
        )
    }

    @Test
    fun write_differentIvEachTime_ciphertextsAreDifferent() {
        val data = "same plaintext".toByteArray()
        val file1 = File(tmpDir, "a.enc")
        val file2 = File(tmpDir, "b.enc")

        store.write(file1, data)
        store.write(file2, data)

        val bytes1 = file1.readBytes()
        val bytes2 = file2.readBytes()

        // IVs are random → ciphertexts must differ even for the same plaintext
        assertFalse(
            "Two encryptions of the same plaintext must produce different ciphertexts",
            bytes1.contentEquals(bytes2),
        )
    }

    // ── 1B.15: Tamper detection ───────────────────────────────────────────────

    @Test(expected = Exception::class)
    fun read_tamperedFile_throwsException() {
        val file = File(tmpDir, "tampered.enc")
        store.write(file, "secret".toByteArray())

        // Corrupt the ciphertext (not the IV — that would change padding, which GCM catches)
        val bytes = file.readBytes().toMutableList()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
        file.writeBytes(bytes.toByteArray())

        store.read(file) // must throw AEADBadTagException
    }

    // ── 1B.15: Delete ─────────────────────────────────────────────────────────

    @Test
    fun delete_removesFile() {
        val file = File(tmpDir, "del.enc")
        store.write(file, "data".toByteArray())
        assertTrue(file.exists())

        store.delete(file)

        assertFalse(file.exists())
    }

    // ── 1B.15: Concurrent encrypt/decrypt ────────────────────────────────────

    @Test
    fun concurrentWrites_allSucceed() = runTest {
        val jobs = (1..10).map { i ->
            async(Dispatchers.Default) {
                val data = "payload_$i".toByteArray()
                val file = File(tmpDir, "concurrent_$i.enc")
                store.write(file, data)
                val result = store.read(file)
                assertArrayEquals("Concurrent write/read $i failed", data, result)
            }
        }
        jobs.awaitAll()
    }

    // ── File layout ───────────────────────────────────────────────────────────

    @Test
    fun write_fileSize_isIvPlusCiphertextPlusAuthTag() {
        val plaintext = ByteArray(100)
        val file = File(tmpDir, "size.enc")

        store.write(file, plaintext)

        val fileSize = file.length()
        val expected = 12L + 100L + 16L // IV + plaintext + GCM auth tag
        assertTrue(
            "File size must be IV(12) + plaintext(${plaintext.size}) + tag(16) = $expected, got $fileSize",
            fileSize == expected,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun containsSubsequence(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun createTempDir(prefix: String): File =
        File(System.getProperty("java.io.tmpdir"), prefix + System.nanoTime()).also { it.mkdirs() }
}
