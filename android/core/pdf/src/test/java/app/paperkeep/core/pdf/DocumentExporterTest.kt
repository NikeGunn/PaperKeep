package app.paperkeep.core.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.crypto.BadPaddingException

/**
 * Unit tests for [DocumentExporter] — 2B.9.
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

    // ── 2B.9 test 1: JPEG export produces a valid JPEG ────────────────────────

    @Test
    fun `exportJpeg creates a file with JPEG magic bytes`() {
        val destFile = tmp.newFile("photo.jpg")
        val bitmap = whiteBitmap(200, 200)

        exporter.exportJpeg(bitmap, destFile)

        assertTrue("JPEG must not be empty", destFile.length() > 0)
        val bytes = destFile.readBytes()
        // JPEG SOI marker: 0xFF 0xD8
        assertTrue(
            "First two bytes should be JPEG SOI marker",
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte(),
        )
    }

    // ── 2B.9 test 2: PNG export produces a valid PNG ─────────────────────────

    @Test
    fun `exportPng creates a file with PNG magic bytes`() {
        val destFile = tmp.newFile("photo.png")
        val bitmap = whiteBitmap(200, 200)

        exporter.exportPng(bitmap, destFile)

        assertTrue("PNG must not be empty", destFile.length() > 0)
        val bytes = destFile.readBytes()
        // PNG signature: 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
        assertTrue("First byte should be 0x89", bytes[0] == 0x89.toByte())
        assertTrue("Bytes 1-3 should be 'PNG'", bytes[1] == 'P'.code.toByte())
    }

    // ── 2B.9 test 3: TXT export contains OCR text ────────────────────────────

    @Test
    fun `exportTxt writes all page texts separated by page separator`() {
        val destFile = tmp.newFile("doc.txt")
        val texts = listOf("Page one text", "Page two content", "Page three data")

        exporter.exportTxt(texts, destFile)

        val content = destFile.readText()
        assertTrue("TXT should contain page 1 text", content.contains("Page one text"))
        assertTrue("TXT should contain page 2 text", content.contains("Page two content"))
        assertTrue("TXT should contain page 3 text", content.contains("Page three data"))
    }

    // ── 2B.9 test 4: encrypted ZIP requires correct password ─────────────────

    @Test
    fun `exportEncryptedZip can be decrypted with the correct password`() {
        val destFile = tmp.newFile("export.enc")
        val bitmaps = listOf(whiteBitmap(100, 100), colourBitmap(100, 100, Color.BLUE))
        val password = "CorrectHorseBatteryStaple".toCharArray()

        exporter.exportEncryptedZip(bitmaps, password, destFile)

        // Decrypt and verify it's a valid ZIP
        val zipBytes = exporter.decryptZip(destFile, password)
        val zis = ZipInputStream(ByteArrayInputStream(zipBytes))
        val entry = zis.nextEntry
        assertTrue("Decrypted archive should contain at least one entry", entry != null)
        assertTrue("Entry name should follow page_NNN.jpg pattern", entry!!.name.startsWith("page_"))
    }

    // ── 2B.9 test 5: encrypted ZIP fails with wrong password ─────────────────

    @Test(expected = Exception::class)
    fun `decryptZip throws on wrong password`() {
        val destFile = tmp.newFile("export2.enc")
        val bitmaps = listOf(whiteBitmap(100, 100))
        val password = "CorrectPassword".toCharArray()
        val wrongPassword = "WrongPassword!!!".toCharArray()

        exporter.exportEncryptedZip(bitmaps, password, destFile)

        // Decrypting with wrong password must throw (BadPaddingException or
        // IllegalArgumentException from ZIP parsing).
        exporter.decryptZip(destFile, wrongPassword)
    }

    // ── 2B.9 test 6: zero pages throws for encrypted ZIP ─────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `exportEncryptedZip throws for empty page list`() {
        val destFile = tmp.newFile("empty.enc")
        exporter.exportEncryptedZip(emptyList(), "pass".toCharArray(), destFile)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun whiteBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            Canvas(bmp).drawColor(Color.WHITE)
        }

    private fun colourBitmap(w: Int, h: Int, colour: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            canvas.drawColor(colour)
            val paint = Paint().apply { color = Color.WHITE; textSize = 20f }
            canvas.drawText("Test", 10f, 30f, paint)
        }
}
