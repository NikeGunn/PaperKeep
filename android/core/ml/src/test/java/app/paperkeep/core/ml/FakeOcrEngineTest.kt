package app.paperkeep.core.ml

import android.graphics.Bitmap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [OcrEngine] contract — 2B.7.
 *
 * Uses [FakeOcrEngine] so the tests are deterministic and fast (no ML model).
 * The contract under test:
 *   1. Returns extracted text for a valid bitmap with content.
 *   2. Returns "" for a bitmap with no text (simulated via emptyText=true).
 *   3. Returns "" for a recycled / corrupted bitmap — never throws.
 */
@RunWith(RobolectricTestRunner::class)
class FakeOcrEngineTest {

    // ── 2B.7 test 1: OCR extracts text from a bitmap that has content ─────────

    @Test
    fun `recognise returns configured text for a valid bitmap`() = runTest {
        val engine = FakeOcrEngine(textToReturn = "Invoice Total: \$42.00")
        val bitmap = Bitmap.createBitmap(400, 100, Bitmap.Config.ARGB_8888)

        val result = engine.recognise(bitmap)

        assertEquals("Invoice Total: \$42.00", result)
    }

    // ── 2B.7 test 2: OCR returns empty string for image without text ──────────

    @Test
    fun `recognise returns empty string when engine is configured for no text`() = runTest {
        val engine = FakeOcrEngine(emptyText = true)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val result = engine.recognise(bitmap)

        assertTrue("Expected empty string for no-text image", result.isEmpty())
    }

    // ── 2B.7 test 3: OCR handles recycled / corrupted bitmap gracefully ───────

    @Test
    fun `recognise returns empty string for recycled bitmap without throwing`() = runTest {
        val engine = FakeOcrEngine(textToReturn = "Should not be returned")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.recycle()

        val result = engine.recognise(bitmap)

        assertTrue("Recycled bitmap must yield empty string, not throw", result.isEmpty())
    }

    // ── 2B.7 bonus: default engine returns non-empty for any valid bitmap ─────

    @Test
    fun `recognise default engine returns non-empty string for normal bitmap`() = runTest {
        val engine = FakeOcrEngine()
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)

        val result = engine.recognise(bitmap)

        assertTrue("Default engine should return text for a valid bitmap", result.isNotEmpty())
    }
}
