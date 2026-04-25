package app.paperkeep.feature.reader

import app.paperkeep.feature.reader.signature.SignaturePlacementState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignaturePlacementStateTest {

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `default placement is bottom-right quadrant`() {
        val s = SignaturePlacementState()
        assertTrue("left should be > 0.5", s.left > 0.5f)
        assertTrue("top should be > 0.5",  s.top  > 0.5f)
        assertTrue("right <= 1",  s.right  <= 1f)
        assertTrue("bottom <= 1", s.bottom <= 1f)
    }

    @Test
    fun `width and height are positive`() {
        val s = SignaturePlacementState()
        assertTrue(s.width  > 0f)
        assertTrue(s.height > 0f)
    }

    // ── translate ─────────────────────────────────────────────────────────────

    @Test
    fun `translate moves box by delta`() {
        val s = SignaturePlacementState(0.1f, 0.1f, 0.4f, 0.3f)
        val leftBefore = s.left
        val topBefore  = s.top
        s.translate(0.05f, 0.02f)
        assertEquals(leftBefore + 0.05f, s.left,  0.001f)
        assertEquals(topBefore  + 0.02f, s.top,   0.001f)
    }

    @Test
    fun `translate clamps to page bounds`() {
        val s = SignaturePlacementState(0.8f, 0.8f, 0.95f, 0.95f)
        s.translate(1.0f, 1.0f)  // large delta — clamp at page boundary
        assertTrue("right must not exceed 1", s.right  <= 1f)
        assertTrue("bottom must not exceed 1", s.bottom <= 1f)
    }

    @Test
    fun `translate clamps at left edge`() {
        val s = SignaturePlacementState(0.1f, 0.1f, 0.4f, 0.4f)
        s.translate(-5f, -5f)
        assertEquals(0f, s.left, 0.001f)
        assertEquals(0f, s.top,  0.001f)
    }

    // ── resizeBottomRight ─────────────────────────────────────────────────────

    @Test
    fun `resizeBottomRight grows the box`() {
        val s = SignaturePlacementState(0.1f, 0.1f, 0.4f, 0.4f)
        val rightBefore  = s.right
        val bottomBefore = s.bottom
        s.resizeBottomRight(0.1f, 0.1f)
        assertEquals(rightBefore  + 0.1f, s.right,  0.001f)
        assertEquals(bottomBefore + 0.1f, s.bottom, 0.001f)
    }

    @Test
    fun `resizeBottomRight cannot make width below MIN_SIZE`() {
        val s = SignaturePlacementState(0.1f, 0.1f, 0.4f, 0.4f)
        s.resizeBottomRight(-10f, -10f)  // drag far left/up
        assertTrue("width must be >= MIN_SIZE",  s.width  >= SignaturePlacementState.MIN_SIZE)
        assertTrue("height must be >= MIN_SIZE", s.height >= SignaturePlacementState.MIN_SIZE)
    }

    @Test
    fun `resizeBottomRight clamps at right=1 and bottom=1`() {
        val s = SignaturePlacementState(0.1f, 0.1f, 0.4f, 0.4f)
        s.resizeBottomRight(10f, 10f)
        assertTrue("right must not exceed 1",  s.right  <= 1f)
        assertTrue("bottom must not exceed 1", s.bottom <= 1f)
    }

    // ── toPixelRect ───────────────────────────────────────────────────────────

    @Test
    fun `toPixelRect scales to page dimensions`() {
        val s = SignaturePlacementState(0.1f, 0.2f, 0.5f, 0.6f)
        val rect = s.toPixelRect(1000, 1000)
        assertEquals(100f,  rect.left,   1f)
        assertEquals(200f,  rect.top,    1f)
        assertEquals(500f,  rect.right,  1f)
        assertEquals(600f,  rect.bottom, 1f)
    }
}
