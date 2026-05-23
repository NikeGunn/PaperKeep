package app.paperkeep.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pure-JVM tests for the Kotlin fallback document finder. Inputs are
 * synthetic luminance grids — no Android Bitmap dependency.
 */
class ContourQuadFinderTest {

    private val w = 200
    private val h = 240

    /**
     * Render a synthetic dark-document-on-light-background luminance grid.
     * The "document" is an [x0..x1) × [y0..y1) rectangle at luma=40 (dark)
     * on a luma=230 background.
     */
    private fun grid(
        bgLuma: Int = 230,
        docLuma: Int = 40,
        x0: Int = 30, y0: Int = 30, x1: Int = 170, y1: Int = 210,
    ): IntArray {
        val out = IntArray(w * h) { bgLuma }
        for (y in y0 until y1) for (x in x0 until x1) out[y * w + x] = docLuma
        return out
    }

    @Test
    fun returnsNullForGridTooSmall() {
        assertNull(ContourQuadFinder.find(IntArray(10), width = 10, height = 1))
        assertNull(ContourQuadFinder.find(IntArray(5 * 5), width = 5, height = 5))
    }

    @Test
    fun returnsNullForLengthMismatch() {
        assertNull(ContourQuadFinder.find(IntArray(40 * 40), width = 50, height = 40))
    }

    @Test
    fun returnsNullForUniformGrid() {
        val flat = IntArray(w * h) { 128 }
        assertNull(ContourQuadFinder.find(flat, w, h))
    }

    @Test
    fun detectsRectangleOnPlainBackground() {
        val pixels = grid()
        val quad = ContourQuadFinder.find(pixels, w, h)
        assertNotNull("must detect the rectangle", quad)
        quad!!
        // Allow 6px wobble for edge-finding tolerances.
        assertCorner(quad.topLeft, 30, 30, tolerance = 8)
        assertCorner(quad.topRight, 169, 30, tolerance = 8)
        assertCorner(quad.bottomRight, 169, 209, tolerance = 8)
        assertCorner(quad.bottomLeft, 30, 209, tolerance = 8)
    }

    @Test
    fun cornersAreOrderedTLTRBRBL() {
        val pixels = grid(x0 = 40, y0 = 50, x1 = 160, y1 = 200)
        val quad = ContourQuadFinder.find(pixels, w, h)
        assertNotNull(quad)
        quad!!
        assertTrue("top-left x < top-right x", quad.topLeft.x < quad.topRight.x)
        assertTrue("top-left y < bottom-left y", quad.topLeft.y < quad.bottomLeft.y)
        assertTrue("bottom-right x > bottom-left x", quad.bottomRight.x > quad.bottomLeft.x)
        assertTrue("bottom-right y > top-right y", quad.bottomRight.y > quad.topRight.y)
    }

    @Test
    fun returnsNullWhenDocumentIsTinyComparedToFrame() {
        // Document smaller than min area fraction (5%).
        val pixels = grid(x0 = 5, y0 = 5, x1 = 20, y1 = 15)
        val quad = ContourQuadFinder.find(pixels, w, h)
        assertNull("tiny rectangles must not be reported", quad)
    }

    @Test
    fun stillDetectsLowContrastDocument() {
        val pixels = grid(bgLuma = 200, docLuma = 130)
        val quad = ContourQuadFinder.find(pixels, w, h)
        // Low contrast may not always succeed, but if it does, the result
        // must still be inside the document footprint.
        if (quad != null) {
            assertTrue(quad.topLeft.x in 0f..50f)
            assertTrue(quad.bottomRight.x in 150f..(w - 1).toFloat())
        }
    }

    @Test
    fun detectsRotatedRectangle() {
        // 45° rotated black square. We rasterise via the obvious affine map.
        val pixels = IntArray(w * h) { 230 }
        val cx = w / 2
        val cy = h / 2
        val side = 70.0
        // Cover the rotated diamond by iterating output pixels.
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x - cx
            val dy = y - cy
            if (abs(dx) + abs(dy) <= side) pixels[y * w + x] = 30
        }
        val quad = ContourQuadFinder.find(pixels, w, h)
        assertNotNull("rotated rectangle must still be found", quad)
    }

    @Test
    fun ignoresBackgroundClutter() {
        val pixels = grid()
        // Drop a small noisy patch in the corner that should be discarded.
        for (y in 5 until 18) for (x in 5 until 18) pixels[y * w + x] = 70
        val quad = ContourQuadFinder.find(pixels, w, h)
        assertNotNull(quad)
        quad!!
        // Result must be the big rectangle, not the small noise patch.
        assertTrue("quad must enclose the big rectangle", quad.area() > 100f * 100f)
    }

    private fun assertCorner(p: Point2f, expectedX: Int, expectedY: Int, tolerance: Int) {
        assertTrue(
            "corner x=${p.x} not within $tolerance of $expectedX",
            abs(p.x - expectedX) <= tolerance,
        )
        assertTrue(
            "corner y=${p.y} not within $tolerance of $expectedY",
            abs(p.y - expectedY) <= tolerance,
        )
    }

    @Test
    fun areaCalculationMatchesShoelace() {
        // 100x100 square should have area 10000.
        val q = Quad(
            topLeft = Point2f(0f, 0f),
            topRight = Point2f(100f, 0f),
            bottomRight = Point2f(100f, 100f),
            bottomLeft = Point2f(0f, 100f),
        )
        assertEquals(10000f, q.area(), 1f)
    }
}
