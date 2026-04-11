package com.scanvault.core.imaging

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for 1B.13: Perspective transform.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PerspectiveTransformTest {

    // ── 1B.13: Correct output for known quad ──────────────────────────────────

    @Test
    fun warp_identityQuad_returnsSameDimensions() {
        val w = 400
        val h = 300
        val bitmap = createSolidBitmap(w, h, Color.RED)
        val quad = fullQuad(w.toFloat(), h.toFloat())

        val result = PerspectiveTransform.warp(bitmap, quad, outputWidth = w, outputHeight = h)

        assertNotNull(result)
        assertEquals(w, result.width)
        assertEquals(h, result.height)
    }

    @Test
    fun warp_producesCorrectOutputSize() {
        val bitmap = createSolidBitmap(800, 600, Color.BLUE)
        val quad = Quad(
            topLeft = Point2f(100f, 100f),
            topRight = Point2f(700f, 100f),
            bottomRight = Point2f(700f, 500f),
            bottomLeft = Point2f(100f, 500f),
        )

        val result = PerspectiveTransform.warp(bitmap, quad, outputWidth = 600, outputHeight = 400)

        assertEquals(600, result.width)
        assertEquals(400, result.height)
    }

    // ── 1B.13: Large image (4000×3000) ────────────────────────────────────────

    @Test
    fun warp_handlesLargeImage_4000x3000() {
        // Uses createBitmap with Robolectric — no real GPU needed
        val bitmap = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        val quad = fullQuad(4000f, 3000f)

        val result = PerspectiveTransform.warp(bitmap, quad)

        assertNotNull(result)
        assertFalse(result.isRecycled)
    }

    // ── 1B.13: Corrupted / edge-case input does not crash ─────────────────────

    @Test
    fun warp_recycledBitmap_returnsInputGracefully() {
        val bitmap = createSolidBitmap(100, 100, Color.WHITE)
        bitmap.recycle()
        val quad = fullQuad(100f, 100f)

        // Should not throw
        val result = PerspectiveTransform.warp(bitmap, quad)
        assertNotNull(result)
    }

    @Test
    fun warp_zeroDimensionQuad_doesNotCrash() {
        val bitmap = createSolidBitmap(200, 200, Color.WHITE)
        val quad = Quad(
            topLeft = Point2f(100f, 100f),
            topRight = Point2f(100f, 100f),
            bottomRight = Point2f(100f, 100f),
            bottomLeft = Point2f(100f, 100f),
        )

        // Degenerate quad — should return bitmap without throwing
        val result = PerspectiveTransform.warp(bitmap, quad)
        assertNotNull(result)
    }

    // ── 1B.14: Rotation ───────────────────────────────────────────────────────

    @Test
    fun rotate_90degrees_swapsDimensions() {
        val bitmap = createSolidBitmap(200, 100, Color.GREEN)

        val rotated = PerspectiveTransform.rotate(bitmap, 90)

        assertEquals(100, rotated.width)
        assertEquals(200, rotated.height)
    }

    @Test
    fun rotate_180degrees_keepsDimensions() {
        val bitmap = createSolidBitmap(200, 100, Color.GREEN)

        val rotated = PerspectiveTransform.rotate(bitmap, 180)

        assertEquals(200, rotated.width)
        assertEquals(100, rotated.height)
    }

    @Test
    fun rotate_360degrees_returnsSameDimensions() {
        val bitmap = createSolidBitmap(300, 200, Color.CYAN)

        val rotated = PerspectiveTransform.rotate(bitmap, 360)

        assertEquals(300, rotated.width)
        assertEquals(200, rotated.height)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun createSolidBitmap(w: Int, h: Int, color: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    private fun fullQuad(w: Float, h: Float) = Quad(
        topLeft = Point2f(0f, 0f),
        topRight = Point2f(w, 0f),
        bottomRight = Point2f(w, h),
        bottomLeft = Point2f(0f, h),
    )
}
