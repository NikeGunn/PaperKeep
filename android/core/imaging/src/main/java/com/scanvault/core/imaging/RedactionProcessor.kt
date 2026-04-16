package com.scanvault.core.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Destructive redaction processor (3B.8).
 *
 * **Irreversible:** pixels inside the redaction rectangle are overwritten
 * with pure black (0xFF000000). The original [Bitmap] passed to [redact]
 * must be mutable — no copy is kept.
 */
object RedactionProcessor {

    /**
     * Fill [rect] with pure black pixels in-place on [bitmap].
     *
     * @param bitmap Must be a mutable ARGB_8888 bitmap.
     * @param rect   The redaction area in pixel coordinates.
     * @throws IllegalArgumentException if [bitmap] is not mutable.
     */
    fun redact(bitmap: Bitmap, rect: Rect) {
        require(bitmap.isMutable) { "bitmap must be mutable for redaction" }
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawRect(rect, paint)
    }

    /**
     * Convenience overload that accepts a [RectF] (float coordinates).
     * Rounds to nearest integer pixel before redacting.
     */
    fun redact(bitmap: Bitmap, rect: RectF) {
        val intRect = Rect(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt(),
        )
        redact(bitmap, intRect)
    }

    /**
     * Remove OCR bounding boxes that overlap any of [redactedRects].
     *
     * Any [OcrBox] whose bounding rectangle intersects a redacted area is
     * removed from the returned list so that the invisible text layer in a
     * PDF is also scrubbed.
     *
     * @param ocrBoxes     All OCR boxes for the page.
     * @param redactedRects List of redacted regions (pixel coordinates).
     * @return A new list with overlapping boxes removed.
     */
    fun eraseOcrBoxes(ocrBoxes: List<OcrBox>, redactedRects: List<RectF>): List<OcrBox> {
        return ocrBoxes.filter { box ->
            redactedRects.none { redacted ->
                RectF.intersects(RectF(box.bounds), redacted)
            }
        }
    }
}

/**
 * Represents a single OCR-recognised word/block with its bounding rectangle.
 */
data class OcrBox(
    val text: String,
    val bounds: Rect,
)
