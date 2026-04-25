package app.paperkeep.feature.reader.signature

import android.graphics.RectF

/**
 * Mutable state for placing and resizing a signature on a page (P3.6).
 *
 * Coordinates are normalised [0, 1] relative to page dimensions so the
 * placement is resolution-independent and can be persisted easily.
 *
 * Default placement: bottom-right quadrant, 30% wide, 15% tall.
 *
 * In a Composable, wrap in `remember { SignaturePlacementState() }` and
 * observe `left/top/right/bottom` with `derivedStateOf` or pass to a Canvas.
 * Outside Compose (e.g. unit tests) the plain vars are directly readable.
 */
class SignaturePlacementState(
    initialLeft:   Float = 0.65f,
    initialTop:    Float = 0.80f,
    initialRight:  Float = 0.95f,
    initialBottom: Float = 0.95f,
) {
    var left:   Float = initialLeft;   private set
    var top:    Float = initialTop;    private set
    var right:  Float = initialRight;  private set
    var bottom: Float = initialBottom; private set

    /** Width in normalised units. Always >= MIN_SIZE. */
    val width:  Float get() = (right  - left).coerceAtLeast(MIN_SIZE)
    /** Height in normalised units. Always >= MIN_SIZE. */
    val height: Float get() = (bottom - top).coerceAtLeast(MIN_SIZE)

    /**
     * Translate the placement box by ([dx], [dy]) in normalised units,
     * clamped so it stays within [0, 1].
     */
    fun translate(dx: Float, dy: Float) {
        val w = width
        val h = height
        left   = (left   + dx).coerceIn(0f, 1f - w)
        top    = (top    + dy).coerceIn(0f, 1f - h)
        right  = left  + w
        bottom = top   + h
    }

    /**
     * Resize by dragging the bottom-right corner by ([dx], [dy]).
     * Minimum size is [MIN_SIZE] in each dimension.
     */
    fun resizeBottomRight(dx: Float, dy: Float) {
        right  = (right  + dx).coerceIn(left + MIN_SIZE, 1f)
        bottom = (bottom + dy).coerceIn(top  + MIN_SIZE, 1f)
    }

    /** Convert to an absolute [RectF] given page [pageWidth] and [pageHeight] in pixels. */
    fun toPixelRect(pageWidth: Int, pageHeight: Int): RectF {
        val pw = pageWidth.toFloat()
        val ph = pageHeight.toFloat()
        val r = RectF()
        r.left   = left   * pw
        r.top    = top    * ph
        r.right  = right  * pw
        r.bottom = bottom * ph
        return r
    }

    companion object {
        const val MIN_SIZE = 0.05f  // 5% of page dimension minimum
    }
}
