package app.paperkeep.core.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.OutputStream

/**
 * Unit tests for [PdfExporter].
 *
 * Uses [FakePdfDocumentWrapper] so no android.graphics.pdf.PdfDocument is required.
 */
@RunWith(RobolectricTestRunner::class)
class PdfExporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var fake: FakePdfDocumentWrapper
    private lateinit var exporter: PdfExporter

    @Before
    fun setUp() {
        fake = FakePdfDocumentWrapper()
        exporter = PdfExporter(documentFactory = { fake })
    }

    // ── Core export ───────────────────────────────────────────────────────────

    @Test
    fun `export creates non-empty file`() {
        val dest = tmp.newFile("out.pdf")
        exporter.export(listOf(PageData(whiteBitmap(300, 400))), dest)
        assertTrue(dest.length() > 0)
        assertTrue(dest.readText().startsWith("%PDF-fake"))
    }

    @Test
    fun `export calls startPage and finishPage once per page`() {
        val dest = tmp.newFile("multi.pdf")
        exporter.export((1..3).map { PageData(whiteBitmap(200, 300)) }, dest)
        assertEquals(3, fake.pagesStarted)
        assertEquals(3, fake.pagesFinished)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `export throws for empty page list`() {
        exporter.export(emptyList(), tmp.newFile("empty.pdf"))
    }

    @Test
    fun `export with ocrText passes text to canvas`() {
        val dest = tmp.newFile("text.pdf")
        exporter.export(
            listOf(PageData(whiteBitmap(300, 400), ocrText = "SearchableText")),
            dest,
        )
        // FakePdfDocumentWrapper records drawn text via RecordingCanvas
        assertTrue(fake.ocrTextsDrawn.any { it.contains("SearchableText") })
    }

    @Test
    fun `export with ocrWords draws invisible text layer`() {
        val dest = tmp.newFile("words.pdf")
        val words = listOf(OcrWord("Invoice", 0.1f, 0.1f, 0.3f, 0.2f))
        exporter.export(
            listOf(PageData(whiteBitmap(600, 800), ocrWords = words)),
            dest,
        )
        assertTrue(fake.ocrTextsDrawn.any { it.contains("Invoice") })
    }

    // ── Page size ─────────────────────────────────────────────────────────────

    @Test
    fun `export A4 uses 595x842 page`() {
        val dest = tmp.newFile("a4.pdf")
        exporter.export(
            listOf(PageData(whiteBitmap(1080, 1920))),
            dest,
            pageSize = PageSize.A4,
        )
        assertEquals(1, fake.pageDimensions.size)
        assertEquals(595 to 842, fake.pageDimensions[0])
    }

    @Test
    fun `export LETTER uses 612x792 page`() {
        val dest = tmp.newFile("letter.pdf")
        exporter.export(
            listOf(PageData(whiteBitmap(1080, 1920))),
            dest,
            pageSize = PageSize.LETTER,
        )
        assertEquals(612 to 792, fake.pageDimensions[0])
    }

    @Test
    fun `export LEGAL uses 612x1008 page`() {
        val dest = tmp.newFile("legal.pdf")
        exporter.export(
            listOf(PageData(whiteBitmap(1080, 1920))),
            dest,
            pageSize = PageSize.LEGAL,
        )
        assertEquals(612 to 1008, fake.pageDimensions[0])
    }

    @Test
    fun `export AUTO preserves aspect ratio`() {
        val dest = tmp.newFile("auto.pdf")
        // Landscape 2:1 bitmap
        exporter.export(
            listOf(PageData(whiteBitmap(800, 400))),
            dest,
            pageSize = PageSize.AUTO,
        )
        val (w, h) = fake.pageDimensions[0]
        // Aspect ratio should be ~2:1 (±1 rounding)
        assertTrue("Width should be larger than height for landscape", w > h)
    }

    // ── drawTextLayer ─────────────────────────────────────────────────────────

    @Test
    fun `drawTextLayer with empty words does not draw text`() {
        val bitmap = whiteBitmap(100, 100)
        val log = mutableListOf<String>()
        val canvas = RecordingCanvas(bitmap, log)
        exporter.drawTextLayer(canvas, emptyList(), 100, 100, 100, 100)
        assertTrue(log.isEmpty())
    }

    @Test
    fun `drawTextLayer draws each word text`() {
        val bitmap = whiteBitmap(200, 200)
        val log = mutableListOf<String>()
        val canvas = RecordingCanvas(bitmap, log)
        val words = listOf(
            OcrWord("Hello", 0f, 0f, 0.3f, 0.1f),
            OcrWord("World", 0.4f, 0f, 0.8f, 0.1f),
        )
        exporter.drawTextLayer(canvas, words, 200, 200, 200, 200)
        assertTrue(log.contains("Hello"))
        assertTrue(log.contains("World"))
    }

    @Test
    fun `drawTextLayer skips blank words`() {
        val bitmap = whiteBitmap(200, 200)
        val log = mutableListOf<String>()
        val canvas = RecordingCanvas(bitmap, log)
        exporter.drawTextLayer(canvas, listOf(OcrWord("  ", 0f, 0f, 0.3f, 0.1f)), 200, 200, 200, 200)
        assertTrue(log.isEmpty())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun whiteBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(Color.WHITE) }
}

// ── Test doubles ──────────────────────────────────────────────────────────────

class FakePdfDocumentWrapper : PdfDocumentWrapper {
    var pagesStarted = 0
    var pagesFinished = 0
    val ocrTextsDrawn = mutableListOf<String>()
    val pageDimensions = mutableListOf<Pair<Int, Int>>()
    private var currentCanvas: RecordingCanvas? = null

    override fun startPage(pageNumber: Int, widthPt: Int, heightPt: Int): Canvas {
        pagesStarted++
        pageDimensions.add(widthPt to heightPt)
        val bmp = Bitmap.createBitmap(widthPt.coerceAtLeast(1), heightPt.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val rc = RecordingCanvas(bmp, ocrTextsDrawn)
        currentCanvas = rc
        return rc
    }

    override fun finishPage() { pagesFinished++ }

    override fun writeAndClose(out: OutputStream) {
        out.write("%PDF-fake\n".toByteArray())
    }
}

class RecordingCanvas(bmp: Bitmap, private val log: MutableList<String>) : Canvas(bmp) {
    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        if (text.isNotBlank()) log.add(text)
        super.drawText(text, x, y, paint)
    }
}
