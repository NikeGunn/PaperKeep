package app.paperkeep.core.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports a list of [PageData] into a PDF file.
 *
 * Each page is an A4-equivalent canvas (595×842 pt at 72 dpi) with:
 *  1. The bitmap image scaled to fill the page.
 *  2. A transparent text layer containing the OCR text (machine-readable /
 *     searchable but invisible to the eye).
 *
 * [documentFactory] creates a fresh [PdfDocumentWrapper] per export call.
 * In production this creates an [AndroidPdfDocumentWrapper].
 * In tests a fake implementation can be injected.
 */
@Singleton
class PdfExporter @Inject constructor(
    private val documentFactory: () -> PdfDocumentWrapper = { AndroidPdfDocumentWrapper() },
) {

    /**
     * Write a multi-page PDF to [destFile].
     *
     * @param pages List of [PageData] in document order. Must not be empty.
     * @param destFile Destination file. Parent directories must exist.
     * @throws IllegalArgumentException if [pages] is empty.
     */
    fun export(pages: List<PageData>, destFile: File) {
        require(pages.isNotEmpty()) { "Cannot create a PDF with zero pages." }

        val doc = documentFactory()
        pages.forEachIndexed { index, page ->
            val canvas = doc.startPage(index + 1, A4_WIDTH_PT, A4_HEIGHT_PT)
            drawPage(canvas, page.bitmap, page.ocrText, A4_WIDTH_PT, A4_HEIGHT_PT)
            doc.finishPage()
        }
        destFile.outputStream().use { doc.writeAndClose(it) }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    internal fun drawPage(
        canvas: Canvas,
        bitmap: Bitmap,
        ocrText: String?,
        pageWidth: Int,
        pageHeight: Int,
    ) {
        canvas.drawColor(Color.WHITE)

        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()
        val scale = minOf(pageWidth / bmpW, pageHeight / bmpH)
        val scaledW = bmpW * scale
        val scaledH = bmpH * scale
        val left = (pageWidth - scaledW) / 2f
        val top = (pageHeight - scaledH) / 2f

        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW.toInt(), scaledH.toInt(), true)
        canvas.drawBitmap(scaled, left, top, null)

        if (!ocrText.isNullOrBlank()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.TRANSPARENT
                textSize = 1f
                alpha = 0
            }
            canvas.drawText(ocrText, 0f, pageHeight.toFloat(), textPaint)
        }
    }

    companion object {
        const val A4_WIDTH_PT = 595
        const val A4_HEIGHT_PT = 842
    }
}

/** Lightweight value type pairing a decoded [bitmap] with optional [ocrText]. */
data class PageData(
    val bitmap: Bitmap,
    val ocrText: String? = null,
)
