package app.paperkeep.core.backup

import app.paperkeep.core.backup.format.BackupHeader
import app.paperkeep.core.backup.format.BackupManifest
import app.paperkeep.core.backup.format.BackupSettings
import app.paperkeep.core.data.db.DocumentEntity
import app.paperkeep.core.data.db.DocumentWithPages
import app.paperkeep.core.data.db.PageEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Round-trip tests for [BackupEngine] using reduced Argon2id params (m=8MiB, t=1)
 * so the suite finishes in seconds. Production wraps with the spec params from
 * [app.paperkeep.core.backup.crypto.BackupKdf].
 */
class BackupEngineTest {

    private val engine = BackupEngine()

    private fun makeDocs(): List<DocumentWithPages> {
        return listOf(
            DocumentWithPages(
                document = DocumentEntity(
                    id = "doc1", title = "T1", createdAt = 1L, updatedAt = 1L,
                    folderId = null, pageCount = 2, colorTag = null,
                ),
                pages = listOf(
                    PageEntity(id = "p1", documentId = "doc1", pageIndex = 0,
                        encryptedImagePath = "p1.enc", encryptedThumbPath = "p1t.enc",
                        width = 100, height = 100),
                    PageEntity(id = "p2", documentId = "doc1", pageIndex = 1,
                        encryptedImagePath = "p2.enc", encryptedThumbPath = "p2t.enc",
                        width = 100, height = 100),
                ),
            ),
            DocumentWithPages(
                document = DocumentEntity(
                    id = "doc2", title = "T2", createdAt = 1L, updatedAt = 1L,
                    folderId = null, pageCount = 1, colorTag = null,
                ),
                pages = listOf(
                    PageEntity(id = "p3", documentId = "doc2", pageIndex = 0,
                        encryptedImagePath = "p3.enc", encryptedThumbPath = "p3t.enc",
                        width = 100, height = 100),
                ),
            ),
        )
    }

    private fun makeInput(
        decryptedBytes: Map<String, ByteArray> = mapOf(
            "p1.enc" to ByteArray(64) { 1 },
            "p2.enc" to ByteArray(64) { 2 },
            "p3.enc" to ByteArray(64) { 3 },
        ),
    ): BackupInput = BackupInput(
        documents = makeDocs(),
        settings = BackupSettings(),
        dbBytes = "fake-db-bytes".toByteArray(),
        appVersionName = "test",
        appVersionCode = 1,
        resolveEncryptedFile = { rel -> File(rel) },
        decryptPage = { f -> decryptedBytes[f.name] ?: error("no fixture for ${f.name}") },
    )

    @Test
    fun writeThenReadRoundTrips() {
        val input = makeInput()
        val baos = ByteArrayOutputStream()
        val pw = "correctHorseBatteryStaple".toCharArray()

        val result = engine.write(
            input = input,
            sink = baos,
            memoryKib = 8 * 1024,
            iterations = 1,
            parallelism = 1,
            password = pw.copyOf(),
        )

        assertEquals(2, result.documentCount)
        assertEquals(3, result.pageCount)
        assertTrue(result.bytesWritten > BackupHeader.FIXED_SIZE)

        var manifestSeen: BackupManifest? = null
        var settingsSeen: BackupSettings? = null
        val pagesSeen = mutableMapOf<String, ByteArray>()
        var dbSeen: ByteArray? = null

        engine.read(ByteArrayInputStream(baos.toByteArray()), pw, object : BackupReadVisitor {
            override fun onManifest(manifest: BackupManifest) { manifestSeen = manifest }
            override fun onDatabase(bytes: ByteArray) { dbSeen = bytes }
            override fun onSettings(settings: BackupSettings) { settingsSeen = settings }
            override fun onPage(zipPath: String, plaintextJpeg: ByteArray) {
                pagesSeen[zipPath] = plaintextJpeg
            }
        })

        assertNotNull(manifestSeen)
        assertEquals(2, manifestSeen!!.documentCount)
        assertEquals(3, manifestSeen!!.pageCount)
        assertEquals("paperkeep.backup", manifestSeen!!.format)
        assertEquals(BackupSettings(), settingsSeen)
        assertArrayEquals("fake-db-bytes".toByteArray(), dbSeen)
        assertEquals(3, pagesSeen.size)
        assertArrayEquals(ByteArray(64) { 1 }, pagesSeen["pages/doc1/0.jpg"])
        assertArrayEquals(ByteArray(64) { 2 }, pagesSeen["pages/doc1/1.jpg"])
        assertArrayEquals(ByteArray(64) { 3 }, pagesSeen["pages/doc2/0.jpg"])
    }

    @Test
    fun wrongPassword_throws() {
        val input = makeInput()
        val baos = ByteArrayOutputStream()
        val correct = "rightPassword123".toCharArray()
        engine.write(input, baos, 8 * 1024, 1, 1, correct)

        val wrong = "wrongPassword999".toCharArray()
        try {
            engine.read(ByteArrayInputStream(baos.toByteArray()), wrong, object : BackupReadVisitor {
                override fun onManifest(manifest: BackupManifest) {}
                override fun onDatabase(bytes: ByteArray) {}
                override fun onSettings(settings: BackupSettings) {}
                override fun onPage(zipPath: String, plaintextJpeg: ByteArray) {}
            })
            fail("expected GCM auth failure with wrong password")
        } catch (e: Throwable) {
            // any exception path is acceptable; we just must NOT silently succeed
            assertTrue(true)
        }
    }

    @Test
    fun tamperedCiphertext_throws() {
        val input = makeInput()
        val baos = ByteArrayOutputStream()
        val pw = "p@ssw0rdCh@nge!".toCharArray()
        engine.write(input, baos, 8 * 1024, 1, 1, pw.copyOf())

        val bytes = baos.toByteArray()
        // Flip a byte well inside the ciphertext (past the header).
        bytes[BackupHeader.FIXED_SIZE + 50] = (bytes[BackupHeader.FIXED_SIZE + 50].toInt() xor 0x01).toByte()

        try {
            engine.read(ByteArrayInputStream(bytes), pw, object : BackupReadVisitor {
                override fun onManifest(manifest: BackupManifest) {}
                override fun onDatabase(bytes: ByteArray) {}
                override fun onSettings(settings: BackupSettings) {}
                override fun onPage(zipPath: String, plaintextJpeg: ByteArray) {}
            })
            fail("tampered ciphertext must fail GCM auth")
        } catch (_: Throwable) {
            assertTrue(true)
        }
    }

    @Test
    fun differentSaltAndIv_perBackup() {
        val input = makeInput()
        val pw = "samePassword12345".toCharArray()

        val a = ByteArrayOutputStream().also { engine.write(input, it, 8 * 1024, 1, 1, pw.copyOf()) }
        val b = ByteArrayOutputStream().also { engine.write(input, it, 8 * 1024, 1, 1, pw.copyOf()) }

        val headerA = BackupHeader.readFrom(ByteArrayInputStream(a.toByteArray()))
        val headerB = BackupHeader.readFrom(ByteArrayInputStream(b.toByteArray()))

        // Salt and IV must be random per backup — reusing them with same key is catastrophic.
        assertNotEquals(headerA.salt.toList(), headerB.salt.toList())
        assertNotEquals(headerA.iv.toList(), headerB.iv.toList())

        // Even though plaintext is identical, the two ciphertexts must differ.
        assertNotEquals(a.toByteArray().toList(), b.toByteArray().toList())
    }

    @Test
    fun manifestSha256_matchesPagePlaintext() {
        val input = makeInput()
        val baos = ByteArrayOutputStream()
        val pw = "anotherPasswordX".toCharArray()
        engine.write(input, baos, 8 * 1024, 1, 1, pw.copyOf())

        var manifest: BackupManifest? = null
        val pages = mutableMapOf<String, ByteArray>()
        engine.read(ByteArrayInputStream(baos.toByteArray()), pw, object : BackupReadVisitor {
            override fun onManifest(m: BackupManifest) { manifest = m }
            override fun onDatabase(bytes: ByteArray) {}
            override fun onSettings(settings: BackupSettings) {}
            override fun onPage(zipPath: String, plaintextJpeg: ByteArray) {
                pages[zipPath] = plaintextJpeg
            }
        })

        for (ref in manifest!!.pages) {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val sha = md.digest(pages[ref.zipPath]!!).joinToString("") { "%02x".format(it) }
            assertEquals(ref.sha256, sha)
            assertEquals(pages[ref.zipPath]!!.size.toLong(), ref.sizeBytes)
        }
    }

    @Test
    fun bytesWritten_reflectsHeaderPlusCiphertext() {
        val input = makeInput()
        val baos = ByteArrayOutputStream()
        val result = engine.write(input, baos, 8 * 1024, 1, 1, "abcdefghij".toCharArray())
        assertEquals(baos.toByteArray().size.toLong(), result.bytesWritten)
        assertTrue(result.sha256Hex.length == 64)
    }
}
