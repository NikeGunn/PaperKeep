package app.paperkeep.core.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Unit tests for [DocumentExporter] — P2.10.
 */
@RunWith(RobolectricTestRunner::class)
class DocumentExporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var exporter: DocumentExporter

    @Before
    fun setUp() {
        exporter = DocumentExporter()
    }

    // ── JPEG ──────────────────────────────────────────────────────────────────

    @Test
    fun `exportJpeg produces JPEG with correct magic bytes`() {
        val dest = tmp.newFile("out.jpg")
        exporter.exportJpeg(whiteBitmap(100, 100), dest)
        val bytes = dest.readBytes()
        assertTrue(dest.length() > 0)
        assertTrue(bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte())
    }

    // ── PNG ───────────────────────────────────────────────────────────────────

    @Test
    fun `exportPng produces PNG with correct magic bytes`() {
        val dest = tmp.newFile("out.png")
        exporter.exportPng(whiteBitmap(100, 100), dest)
        val bytes = dest.readBytes()
        assertTrue(dest.length() > 0)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
    }

    // ── JPEG ZIP ──────────────────────────────────────────────────────────────

    @Test
    fun `exportJpegZip creates ZIP with one entry per page`() {
        val dest = tmp.newFile("pages.zip")
        val bitmaps = (1..3).map { whiteBitmap(50, 50) }
        exporter.exportJpegZip(bitmaps, dest)

        val entries = mutableListOf<String>()
        ZipInputStream(dest.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) { entries.add(e.name); e = zis.nextEntry }
        }
        assertEquals(3, entries.size)
        assertTrue(entries[0].startsWith("page_001"))
        assertTrue(entries[2].startsWith("page_003"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exportJpegZip throws for empty list`() {
        exporter.exportJpegZip(emptyList(), tmp.newFile("e.zip"))
    }

    // ── TXT ───────────────────────────────────────────────────────────────────

    @Test
    fun `exportTxt writes all pages separated by dashes`() {
        val dest = tmp.newFile("doc.txt")
        exporter.exportTxt(listOf("Page one", "Page two", "Page three"), dest)
        val content = dest.readText()
        assertTrue(content.contains("Page one"))
        assertTrue(content.contains("Page two"))
        assertTrue(content.contains("Page three"))
        assertTrue(content.contains("---"))
    }

    @Test
    fun `exportTxt with single page has no separator`() {
        val dest = tmp.newFile("single.txt")
        exporter.exportTxt(listOf("Only page"), dest)
        val content = dest.readText()
        assertTrue(content.contains("Only page"))
        assertTrue(!content.contains("---"))
    }

    // ── Encrypted ZIP (Argon2id + AES-256-GCM) ───────────────────────────────

    @Test
    fun `exportEncryptedZip round-trip with correct password`() {
        val dest = tmp.newFile("export.enc")
        val bitmaps = listOf(whiteBitmap(50, 50), whiteBitmap(50, 50))
        val password = "CorrectHorseBattery".toCharArray()

        exporter.exportEncryptedZip(bitmaps, password.copyOf(), dest)

        val zipBytes = exporter.decryptZip(dest, password.copyOf())
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) { entries.add(e.name); e = zis.nextEntry }
        }
        assertEquals(2, entries.size)
        assertTrue(entries[0].endsWith(".jpg"))
    }

    @Test(expected = Exception::class)
    fun `decryptZip throws on wrong password`() {
        val dest = tmp.newFile("export2.enc")
        exporter.exportEncryptedZip(listOf(whiteBitmap(50, 50)), "correct".toCharArray(), dest)
        exporter.decryptZip(dest, "wrong_password_123".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exportEncryptedZip throws for empty list`() {
        exporter.exportEncryptedZip(emptyList(), "pass".toCharArray(), tmp.newFile("e.enc"))
    }

    @Test
    fun `exportEncryptedZip output is opaque — not a valid ZIP`() {
        val dest = tmp.newFile("opaque.enc")
        exporter.exportEncryptedZip(listOf(whiteBitmap(50, 50)), "pass".toCharArray(), dest)
        // The raw file should NOT start with PK (ZIP magic bytes)
        val bytes = dest.readBytes()
        assertNotEquals(0x50.toByte(), bytes[0]) // 'P' of PK
    }

    @Test
    fun `deriveKeyArgon2id produces 32-byte key`() {
        val key = exporter.deriveKeyArgon2id("password".toCharArray(), ByteArray(32) { it.toByte() })
        assertEquals(32, key.size)
    }

    @Test
    fun `deriveKeyArgon2id different salts produce different keys`() {
        val salt1 = ByteArray(32) { 0 }
        val salt2 = ByteArray(32) { 1 }
        val k1 = exporter.deriveKeyArgon2id("password".toCharArray(), salt1)
        val k2 = exporter.deriveKeyArgon2id("password".toCharArray(), salt2)
        assertNotEquals(k1.toList(), k2.toList())
    }

    @Test
    fun `deriveKeyArgon2id different passwords produce different keys`() {
        val salt = ByteArray(32) { it.toByte() }
        val k1 = exporter.deriveKeyArgon2id("password1".toCharArray(), salt)
        val k2 = exporter.deriveKeyArgon2id("password2".toCharArray(), salt)
        assertNotEquals(k1.toList(), k2.toList())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun whiteBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(Color.WHITE) }
}
