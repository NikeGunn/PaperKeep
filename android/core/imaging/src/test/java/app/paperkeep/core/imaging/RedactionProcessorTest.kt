package app.paperkeep.core.imaging

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class RedactionProcessorTest {

    @Test
    fun `redacted pixels are pure black 0xFF000000`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        val rect = Rect(10, 10, 50, 50)
        RedactionProcessor.redact(bitmap, rect)

        // All pixels inside the rect must be pure black
        for (x in 10 until 50) {
            for (y in 10 until 50) {
                val pixel = bitmap.getPixel(x, y)
                assertEquals(
                    "Pixel at ($x,$y) inside redacted rect must be 0xFF000000",
                    0xFF000000.toInt(),
                    pixel,
                )
            }
        }
    }

    @Test
    fun `pixels outside redaction area are unchanged`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        val rect = Rect(10, 10, 50, 50)
        RedactionProcessor.redact(bitmap, rect)

        // Pixel at (5,5) is well outside the redaction rect — must stay white
        assertEquals(Color.WHITE, bitmap.getPixel(5, 5))
        assertEquals(Color.WHITE, bitmap.getPixel(90, 90))
    }

    @Test
    fun `redaction is irreversible — no copy kept`() {
        // The processor must modify the bitmap in-place: the same object is
        // used. There is no "original" copy stored by RedactionProcessor.
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        RedactionProcessor.redact(bitmap, Rect(0, 0, 100, 100))

        // The bitmap itself must be all black — irreversible
        for (x in 0 until 100 step 10) {
            for (y in 0 until 100 step 10) {
                assertEquals(0xFF000000.toInt(), bitmap.getPixel(x, y))
            }
        }
    }

    @Test
    fun `eraseOcrBoxes removes boxes that intersect redacted area`() {
        val boxes = listOf(
            OcrBox("inside", Rect(20, 20, 40, 40)),
            OcrBox("outside", Rect(60, 60, 80, 80)),
        )
        val redacted = listOf(RectF(10f, 10f, 50f, 50f))

        val result = RedactionProcessor.eraseOcrBoxes(boxes, redacted)

        assertEquals(1, result.size)
        assertEquals("outside", result.first().text)
    }

    @Test
    fun `eraseOcrBoxes keeps all boxes when no overlap`() {
        val boxes = listOf(
            OcrBox("a", Rect(0, 0, 10, 10)),
            OcrBox("b", Rect(20, 20, 30, 30)),
        )
        val redacted = listOf(RectF(50f, 50f, 100f, 100f))

        val result = RedactionProcessor.eraseOcrBoxes(boxes, redacted)
        assertEquals(2, result.size)
    }

    // ── Raw-pixel destructive verification ────────────────────────────────────

    @Test
    fun `full-bitmap redaction leaves no white pixels`() {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        RedactionProcessor.redact(bitmap, Rect(0, 0, 50, 50))

        for (x in 0 until 50 step 5) {
            for (y in 0 until 50 step 5) {
                assertEquals(
                    "Every sampled pixel must be black after full redaction",
                    0xFF000000.toInt(), bitmap.getPixel(x, y),
                )
            }
        }
    }

    @Test
    fun `RectF overload redacts correct region`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        RedactionProcessor.redact(bitmap, RectF(20f, 20f, 60f, 60f))

        assertEquals(0xFF000000.toInt(), bitmap.getPixel(30, 30))  // inside
        assertEquals(Color.WHITE,        bitmap.getPixel(10, 10))  // outside
    }

    @Test(expected = IllegalArgumentException::class)
    fun `immutable bitmap throws IllegalArgumentException`() {
        val immutable = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
            .copy(Bitmap.Config.ARGB_8888, false)  // immutable copy
        RedactionProcessor.redact(immutable, Rect(0, 0, 10, 10))
    }

    @Test
    fun `eraseOcrBoxes with empty box list returns empty`() {
        val result = RedactionProcessor.eraseOcrBoxes(emptyList(), listOf(RectF(0f, 0f, 100f, 100f)))
        assertEquals(0, result.size)
    }

    @Test
    fun `eraseOcrBoxes with empty redaction list preserves all boxes`() {
        val boxes = listOf(OcrBox("text", Rect(0, 0, 10, 10)))
        val result = RedactionProcessor.eraseOcrBoxes(boxes, emptyList())
        assertEquals(1, result.size)
    }
}
