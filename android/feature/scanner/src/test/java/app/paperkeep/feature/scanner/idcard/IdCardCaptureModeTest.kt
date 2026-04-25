package app.paperkeep.feature.scanner.idcard

import android.graphics.Bitmap
import android.graphics.Color
import app.paperkeep.feature.scanner.IdCardCaptureMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class IdCardCaptureModeTest {

    private lateinit var mode: IdCardCaptureMode

    @Before
    fun setUp() {
        mode = IdCardCaptureMode()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has no front or back`() {
        assertFalse(mode.hasFront)
        assertFalse(mode.hasBack)
    }

    @Test
    fun `hasFront true after captureFront`() {
        mode.captureFront(whiteBitmap(100, 60))
        assertTrue(mode.hasFront)
    }

    @Test
    fun `hasBack true after captureBack`() {
        mode.captureBack(whiteBitmap(100, 60))
        assertTrue(mode.hasBack)
    }

    @Test
    fun `reset clears both sides`() {
        mode.captureFront(whiteBitmap(100, 60))
        mode.captureBack(whiteBitmap(100, 60))
        mode.reset()
        assertFalse(mode.hasFront)
        assertFalse(mode.hasBack)
    }

    // ── buildComposite — error paths ─────────────────────────────────────────

    @Test(expected = IllegalStateException::class)
    fun `buildComposite throws when front missing`() {
        mode.captureBack(whiteBitmap(100, 60))
        mode.buildComposite()
    }

    @Test(expected = IllegalStateException::class)
    fun `buildComposite throws when back missing`() {
        mode.captureFront(whiteBitmap(100, 60))
        mode.buildComposite()
    }

    // ── buildComposite — page dimensions ──────────────────────────────────────

    @Test
    fun `composite page is A4 dimensions`() {
        mode.captureFront(whiteBitmap(200, 120))
        mode.captureBack(whiteBitmap(200, 120))
        val doc = mode.buildComposite()
        assertEquals(IdCardCaptureMode.A4_W, doc.page.width)
        assertEquals(IdCardCaptureMode.A4_H, doc.page.height)
    }

    @Test
    fun `composite page is ARGB_8888`() {
        mode.captureFront(whiteBitmap(200, 120))
        mode.captureBack(whiteBitmap(200, 120))
        val doc = mode.buildComposite()
        assertEquals(Bitmap.Config.ARGB_8888, doc.page.config)
    }

    // ── buildComposite — content placement ────────────────────────────────────

    @Test
    fun `composite page has white background`() {
        // A corner pixel should be white since nothing is placed there
        mode.captureFront(whiteBitmap(10, 10))
        mode.captureBack(whiteBitmap(10, 10))
        val doc = mode.buildComposite()
        // Top-left corner — should be white (no card content reaches the very corner due to padding)
        val topLeft = doc.page.getPixel(0, 0)
        assertEquals(Color.WHITE, topLeft)
    }

    @Test
    fun `buildComposite produces non-null page`() {
        mode.captureFront(whiteBitmap(200, 120))
        mode.captureBack(whiteBitmap(200, 120))
        val doc = mode.buildComposite()
        assertNotNull(doc.page)
    }

    @Test
    fun `buildComposite can be called twice with same inputs`() {
        val front = whiteBitmap(100, 60)
        val back  = whiteBitmap(100, 60)
        mode.captureFront(front)
        mode.captureBack(back)
        val doc1 = mode.buildComposite()
        val doc2 = mode.buildComposite()
        assertEquals(doc1.page.width, doc2.page.width)
        assertEquals(doc1.page.height, doc2.page.height)
    }

    // ── Slot geometry ─────────────────────────────────────────────────────────

    @Test
    fun `half height constant equals A4_H divided by 2`() {
        assertEquals(IdCardCaptureMode.A4_H / 2, IdCardCaptureMode.HALF_H)
    }

    @Test
    fun `slot padding is positive`() {
        assertTrue(IdCardCaptureMode.SLOT_PADDING > 0)
    }

    @Test
    fun `very small source bitmaps do not throw`() {
        mode.captureFront(whiteBitmap(1, 1))
        mode.captureBack(whiteBitmap(1, 1))
        val doc = mode.buildComposite()
        assertNotNull(doc.page)
    }

    @Test
    fun `landscape source bitmap produces valid composite`() {
        mode.captureFront(whiteBitmap(856, 540))  // standard ID card aspect
        mode.captureBack(whiteBitmap(856, 540))
        val doc = mode.buildComposite()
        assertEquals(IdCardCaptureMode.A4_W, doc.page.width)
        assertEquals(IdCardCaptureMode.A4_H, doc.page.height)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun whiteBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE) }
}
