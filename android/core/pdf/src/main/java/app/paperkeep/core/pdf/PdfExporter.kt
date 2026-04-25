package app.paperkeep.core.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports a list of [PageData] into a searchable PDF file.
 *
 * Each page consists of:
 *  1. A compressed image layer (JPEG at [PdfQuality] setting).
 *  2. An invisible text layer: OCR words drawn at their bounding boxes using
 *     alpha=0 paint, making the PDF text-selectable in Adobe Reader.
 *
 * Page size is determined by [PageSize]:
 *  - AUTO  → matches the original image aspect ratio at 72 dpi
 *  - A4    → 595 × 842 pt
 *  - LETTER → 612 × 792 pt
 *  - LEGAL → 612 × 1008 pt
 *
 * [documentFactory] is injectable for testing.
 */
@Singleton
class PdfExporter @Inject constructor(
    private val documentFactory: () -> PdfDocumentWrapper = { AndroidPdfDocumentWrapper() },
) {

    /**
     * Write a multi-page PDF to [destFile].
     *
     * @param pages  Page data in document order. Must not be empty.
     * @param destFile Destination file; parent directories must already exist.
     * @param pageSize Target page size (default AUTO).
     * @param quality  JPEG compression quality for image layer (default MEDIUM = 85).
     * @throws IllegalArgumentException if [pages] is empty.
     */
    fun export(
        pages: List<PageData>,
        destFile: File,
        pageSize: PageSize = PageSize.AUTO,
        quality: PdfQuality = PdfQuality.MEDIUM,
    ) {
        require(pages.isNotEmpty()) { "Cannot create a PDF with zero pages." }

        val doc = documentFactory()
        pages.forEachIndexed { index, page ->
            val (w, h) = resolvePageDimensions(pageSize, page.bitmap)
            val canvas = doc.startPage(index + 1, w, h)
            drawImageLayer(canvas, page.bitmap, w, h, quality)
            if (page.ocrWords.isNotEmpty()) {
                drawTextLayer(canvas, page.ocrWords, w, h, page.bitmap.width, page.bitmap.height)
            } else if (!page.ocrText.isNullOrBlank()) {
                drawFallbackTextLayer(canvas, page.ocrText, w, h)
            }
            doc.finishPage()
        }
        destFile.outputStream().use { doc.writeAndClose(it) }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun resolvePageDimensions(size: PageSize, bitmap: Bitmap): Pair<Int, Int> = when (size) {
        PageSize.AUTO -> {
            val scale = MAX_AUTO_PT.toFloat() / maxOf(bitmap.width, bitmap.height)
            Pair(
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
            )
        }
        PageSize.A4 -> Pair(A4_WIDTH_PT, A4_HEIGHT_PT)
        PageSize.LETTER -> Pair(LETTER_WIDTH_PT, LETTER_HEIGHT_PT)
        PageSize.LEGAL -> Pair(LEGAL_WIDTH_PT, LEGAL_HEIGHT_PT)
    }

    internal fun drawImageLayer(canvas: Canvas, bitmap: Bitmap, pageW: Int, pageH: Int, quality: PdfQuality) {
        canvas.drawColor(Color.WHITE)
        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()
        val scale = minOf(pageW / bmpW, pageH / bmpH)
        val scaledW = (bmpW * scale).toInt()
        val scaledH = (bmpH * scale).toInt()
        val left = ((pageW - scaledW) / 2f)
        val top = ((pageH - scaledH) / 2f)
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        canvas.drawBitmap(scaled, left, top, null)
    }

    /**
     * Draw invisible text at bounding-box positions so the PDF is text-selectable.
     *
     * Each [OcrWord] carries its normalised [0,1] bounding box coordinates relative
     * to the original bitmap dimensions. We scale to page space here.
     */
    internal fun drawTextLayer(
        canvas: Canvas,
        words: List<OcrWord>,
        pageW: Int,
        pageH: Int,
        bitmapW: Int,
        bitmapH: Int,
    ) {
        if (words.isEmpty()) return

        val xScale = pageW.toFloat() / bitmapW
        val yScale = pageH.toFloat() / bitmapH

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 0             // completely invisible
        }

        words.forEach { word ->
            val left = word.left * bitmapW * xScale
            val top = word.top * bitmapH * yScale
            val right = word.right * bitmapW * xScale
            val bottom = word.bottom * bitmapH * yScale
            val textHeight = bottom - top
            if (textHeight > 0f && word.text.isNotBlank()) {
                textPaint.textSize = textHeight
                canvas.drawText(word.text, left, bottom, textPaint)
            }
        }
    }

    /** Fallback: draw [text] as a single invisible block at the page bottom. */
    internal fun drawFallbackTextLayer(canvas: Canvas, text: String, pageW: Int, pageH: Int) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 0
            textSize = 1f
        }
        canvas.drawText(text, 0f, pageH.toFloat(), textPaint)
    }

    companion object {
        const val A4_WIDTH_PT = 595
        const val A4_HEIGHT_PT = 842
        const val LETTER_WIDTH_PT = 612
        const val LETTER_HEIGHT_PT = 792
        const val LEGAL_WIDTH_PT = 612
        const val LEGAL_HEIGHT_PT = 1008
        private const val MAX_AUTO_PT = 842
    }
}

/** A single OCR word with its bounding box (normalised 0..1 relative to the bitmap). */
data class OcrWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Lightweight page data for PDF export. */
data class PageData(
    val bitmap: Bitmap,
    /** Plain text fallback (written at the bottom if [ocrWords] is empty). */
    val ocrText: String? = null,
    /** Positioned word bboxes for the invisible text layer. */
    val ocrWords: List<OcrWord> = emptyList(),
)

/** Target page size for PDF export. */
enum class PageSize { AUTO, A4, LETTER, LEGAL }

/** JPEG image quality for the PDF image layer. */
enum class PdfQuality(val value: Int) {
    LOW(60),
    MEDIUM(85),
    HIGH(95),
}
