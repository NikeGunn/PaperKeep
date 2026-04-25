package app.paperkeep.feature.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Two-step ID card capture mode (P3.2).
 *
 * Composites front and back sides onto a **single portrait A4 page**:
 *  - Top half  → front side (centred, scaled to fit the slot)
 *  - Bottom half → back side (centred, scaled to fit the slot)
 *
 * The resulting page is 595 × 842 px (1 pt = 1 px in this representation)
 * and has a white background with a 1 px grey divider between the halves.
 *
 * Usage:
 * 1. [captureFront] — store the front-side bitmap.
 * 2. [captureBack]  — store the back-side bitmap.
 * 3. [buildComposite] — returns the single [CompositeDocument].
 *
 * Calling [reset] clears both sides so the object can be reused for
 * a fresh capture without reallocating.
 */
class IdCardCaptureMode {

    private var frontBitmap: Bitmap? = null
    private var backBitmap: Bitmap? = null

    val hasFront: Boolean get() = frontBitmap != null
    val hasBack: Boolean get() = backBitmap != null

    fun captureFront(bitmap: Bitmap) {
        frontBitmap = bitmap
    }

    fun captureBack(bitmap: Bitmap) {
        backBitmap = bitmap
    }

    fun reset() {
        frontBitmap = null
        backBitmap = null
    }

    /**
     * Build the single-page A4 composite.
     *
     * @throws IllegalStateException if either side has not been captured yet.
     */
    fun buildComposite(): CompositeDocument {
        val front = checkNotNull(frontBitmap) { "Front side not captured" }
        val back  = checkNotNull(backBitmap)  { "Back side not captured"  }

        val page = Bitmap.createBitmap(A4_W, A4_H, Bitmap.Config.ARGB_8888)
        page.eraseColor(Color.WHITE)
        val canvas = Canvas(page)

        // Top half: front side
        renderInSlot(canvas, front, slotTop = 0, slotHeight = HALF_H)

        // Divider
        val dividerPaint = Paint().apply {
            color = DIVIDER_COLOR
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(0f, HALF_H.toFloat(), A4_W.toFloat(), HALF_H.toFloat(), dividerPaint)

        // Bottom half: back side
        renderInSlot(canvas, back, slotTop = HALF_H, slotHeight = HALF_H)

        return CompositeDocument(page = page)
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /** Scale [source] to fit inside a horizontal slot and centre it. */
    private fun renderInSlot(
        canvas: Canvas,
        source: Bitmap,
        slotTop: Int,
        slotHeight: Int,
    ) {
        val availW = A4_W - 2 * SLOT_PADDING
        val availH = slotHeight - 2 * SLOT_PADDING

        val scale = minOf(
            availW.toFloat() / source.width,
            availH.toFloat() / source.height,
        ).coerceAtMost(1f)  // never upscale

        val scaledW = (source.width  * scale).toInt().coerceAtLeast(1)
        val scaledH = (source.height * scale).toInt().coerceAtLeast(1)
        val left    = (A4_W - scaledW) / 2f
        val top     = slotTop + (slotHeight - scaledH) / 2f

        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        canvas.drawBitmap(scaled, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    companion object {
        const val A4_W = 595
        const val A4_H = 842
        const val HALF_H = A4_H / 2          // 421 px per half
        const val SLOT_PADDING = 16          // px margin inside each half
        val DIVIDER_COLOR = Color.LTGRAY
    }
}

/**
 * Result of a composite ID card operation.
 *
 * [page] A single A4 bitmap with front on top, back on bottom.
 */
data class CompositeDocument(
    val page: Bitmap,
)
