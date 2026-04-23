package app.paperkeep.core.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Unit tests for [PdfExporter] — 2B.8.
 *
 * Uses a [FakePdfDocumentWrapper] that writes a synthetic PDF-like byte sequence
 * instead of calling [android.graphics.pdf.PdfDocument] (which is not supported
 * under Robolectric unit tests).
 */
@RunWith(RobolectricTestRunner::class)
class PdfExporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var fake: FakePdfDocumentWrapper
    private lateinit var exporter: PdfExporter
    private val canvasCallLog = mutableListOf<String>()

    @Before
    fun setUp() {
        fake = FakePdfDocumentWrapper()
        exporter = PdfExporter(documentFactory = { fake })
        canvasCallLog.clear()
    }

    // ── 2B.8 test 1: single-page PDF creates a valid (non-empty) output ───────

    @Test
    fun `export creates a non-empty PDF file for a single page`() {
        val destFile = tmp.newFile("single.pdf")
        val pages = listOf(PageData(bitmap = whiteBitmap(300, 400)))

        exporter.export(pages, destFile)

        assertTrue("PDF file should not be empty", destFile.length() > 0)
        // Fake wrapper writes "%PDF-fake" header
        val content = destFile.readText(Charsets.UTF_8)
        assertTrue("Output should contain synthetic PDF marker", content.startsWith("%PDF-fake"))
    }

    // ── 2B.8 test 2: PDF has searchable text layer ────────────────────────────

    @Test
    fun `export passes OCR text to the page canvas`() {
        val destFile = tmp.newFile("searchable.pdf")
        val ocrText = "SearchableInvoiceText123"
        val pages = listOf(PageData(bitmap = whiteBitmap(300, 400), ocrText = ocrText))

        exporter.export(pages, destFile)

        // The fake wrapper records the ocrText drawn on each page canvas
        assertTrue(
            "OCR text '$ocrText' should appear in fake output",
            fake.ocrTextsDrawn.contains(ocrText),
        )
    }

    // ── 2B.8 test 3: multi-page PDF has the correct page count ────────────────

    @Test
    fun `export calls startPage once per page`() {
        val destFile = tmp.newFile("multi.pdf")
        val pages = (1..3).map {
            PageData(bitmap = whiteBitmap(200, 200))
        }

        exporter.export(pages, destFile)

        assertEquals("startPage should be called 3 times", 3, fake.pagesStarted)
        assertEquals("finishPage should be called 3 times", 3, fake.pagesFinished)
    }

    // ── 2B.8 test 4: zero pages throws IllegalArgumentException ───────────────

    @Test(expected = IllegalArgumentException::class)
    fun `export throws for empty page list`() {
        val destFile = tmp.newFile("empty.pdf")
        exporter.export(emptyList(), destFile)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun whiteBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            Canvas(bmp).drawColor(Color.WHITE)
        }
}

/**
 * Test double for [PdfDocumentWrapper] that:
 *  - Counts how many times pages are started / finished.
 *  - Captures OCR text that would be drawn (via [PdfExporter.drawPage] indirectly —
 *    here we expose the canvas so the exporter can draw, and we inspect the page data).
 *  - Writes a synthetic "%PDF-fake\n" header so the destination file is non-empty.
 */
class FakePdfDocumentWrapper : PdfDocumentWrapper {

    var pagesStarted = 0
    var pagesFinished = 0
    val ocrTextsDrawn = mutableListOf<String>()

    // We wrap a FakeCanvas to capture drawText calls
    private var currentFakeCanvas: FakeCanvas? = null

    override fun startPage(pageNumber: Int, widthPt: Int, heightPt: Int): Canvas {
        pagesStarted++
        val fc = FakeCanvas(ocrTextsDrawn)
        currentFakeCanvas = fc
        return fc.canvas
    }

    override fun finishPage() {
        pagesFinished++
        currentFakeCanvas = null
    }

    override fun writeAndClose(out: OutputStream) {
        out.write("%PDF-fake\n".toByteArray(Charsets.UTF_8))
    }
}

/**
 * Wraps a real [Bitmap]-backed [Canvas] and intercepts [Canvas.drawText] calls
 * to record any text drawn (e.g. the OCR text layer).
 *
 * We use a real Canvas so Robolectric shadows can handle drawBitmap/drawColor.
 */
class FakeCanvas(private val textLog: MutableList<String>) {

    private val bmp = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)

    // Expose the real canvas; drawText interception happens via a shadow in
    // Robolectric — but since we can't easily intercept drawText, we instead
    // patch PdfExporter to expose drawPage as internal so we can inspect ocrText
    // directly at the exporter level. The FakePdfDocumentWrapper test above
    // verifies ocrTextsDrawn via a second path: PdfExporter calls drawPage
    // which calls canvas.drawText with the ocrText. We expose a simple
    // recording canvas subclass below.
    val canvas: Canvas = RecordingCanvas(bmp, textLog)
}

/**
 * [Canvas] subclass that records strings passed to [drawText].
 */
class RecordingCanvas(bmp: Bitmap, private val log: MutableList<String>) : Canvas(bmp) {
    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        if (text.isNotBlank()) log.add(text)
        super.drawText(text, x, y, paint)
    }
}
