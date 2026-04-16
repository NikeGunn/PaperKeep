package com.scanvault.feature.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
/**
 * Two-step ID card capture mode (3B.2).
 *
 * Usage:
 * 1. Call [captureFront] with the front-side bitmap.
 * 2. Call [captureBack] with the back-side bitmap.
 * 3. Call [buildComposite] to receive a [CompositeDocument] with two A4 pages.
 *
 * Each side is placed on its own A4 page (595 × 842 pt).
 * The bitmap is centred and scaled to fit while preserving aspect ratio.
 */
class IdCardCaptureMode {

    private var frontBitmap: Bitmap? = null
    private var backBitmap: Bitmap? = null

    /** Step 1: record the front-side capture. */
    fun captureFront(bitmap: Bitmap) {
        frontBitmap = bitmap
    }

    /** Step 2: record the back-side capture. */
    fun captureBack(bitmap: Bitmap) {
        backBitmap = bitmap
    }

    /**
     * Build the composite A4 document.
     *
     * @throws IllegalStateException if front or back capture has not been provided.
     */
    fun buildComposite(): CompositeDocument {
        val front = checkNotNull(frontBitmap) { "Front side not captured" }
        val back = checkNotNull(backBitmap) { "Back side not captured" }

        val frontPage = renderOnA4(front)
        val backPage = renderOnA4(back)

        return CompositeDocument(pages = listOf(frontPage, backPage))
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Scale [source] to fit on an A4 canvas, centred, white background. */
    private fun renderOnA4(source: Bitmap): Bitmap {
        val page = Bitmap.createBitmap(A4_WIDTH_PT, A4_HEIGHT_PT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)

        val scale = minOf(
            A4_WIDTH_PT.toFloat() / source.width,
            A4_HEIGHT_PT.toFloat() / source.height,
        )
        val scaledW = (source.width * scale).toInt()
        val scaledH = (source.height * scale).toInt()
        val left = (A4_WIDTH_PT - scaledW) / 2f
        val top = (A4_HEIGHT_PT - scaledH) / 2f

        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        canvas.drawBitmap(scaled, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
        return page
    }
}

private const val A4_WIDTH_PT = 595
private const val A4_HEIGHT_PT = 842

/**
 * Result of an ID card composite operation.
 *
 * @param pages Always contains exactly 2 entries: index 0 = front, index 1 = back.
 */
data class CompositeDocument(
    val pages: List<Bitmap>,
)
