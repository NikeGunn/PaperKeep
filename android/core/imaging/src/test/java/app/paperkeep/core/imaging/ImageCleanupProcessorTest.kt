package app.paperkeep.core.imaging

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ImageCleanupProcessorTest {

    /** Create a 20×20 ARGB bitmap with a mix of pixels for filter testing. */
    private fun noisyBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        for (x in 0 until 20) {
            for (y in 0 until 20) {
                // Alternate light/dark pixels to give noise to denoise / sharpen
                val v = if ((x + y) % 2 == 0) 200 else 50
                bmp.setPixel(x, y, Color.rgb(v, v, v))
            }
        }
        return bmp
    }

    private fun pixelDifference(a: Bitmap, b: Bitmap): Int {
        var diff = 0
        for (x in 0 until a.width) {
            for (y in 0 until a.height) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) diff++
            }
        }
        return diff
    }

    // ── DENOISE ───────────────────────────────────────────────────────────────

    @Test
    fun `denoise output differs from input`() {
        val input = noisyBitmap()
        val output = ImageCleanupProcessor.denoise(input)
        // Box blur of a noisy bitmap should change pixels
        val diff = pixelDifference(input, output)
        assertNotEquals("denoise output should differ from input", 0, diff)
    }

    @Test
    fun `denoise does not modify original bitmap`() {
        val input = noisyBitmap()
        val pixelBefore = input.getPixel(5, 5)
        ImageCleanupProcessor.denoise(input)
        assertEquals("denoise must not modify original", pixelBefore, input.getPixel(5, 5))
    }

    // ── SHARPEN ───────────────────────────────────────────────────────────────

    @Test
    fun `sharpen output differs from input`() {
        val input = noisyBitmap()
        val output = ImageCleanupProcessor.sharpen(input)
        val diff = pixelDifference(input, output)
        assertNotEquals("sharpen output should differ from input", 0, diff)
    }

    @Test
    fun `sharpen does not modify original bitmap`() {
        val input = noisyBitmap()
        val pixelBefore = input.getPixel(5, 5)
        ImageCleanupProcessor.sharpen(input)
        assertEquals("sharpen must not modify original", pixelBefore, input.getPixel(5, 5))
    }

    // ── FIX_LIGHTING ──────────────────────────────────────────────────────────

    @Test
    fun `fixLighting output differs from input`() {
        // Use a mid-grey bitmap so brightness adjustment changes pixels
        val input = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(100, 100, 100))
        val output = ImageCleanupProcessor.fixLighting(input)
        val diff = pixelDifference(input, output)
        assertNotEquals("fixLighting output should differ from input", 0, diff)
    }

    @Test
    fun `fixLighting does not modify original bitmap`() {
        val input = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(100, 100, 100))
        val pixelBefore = input.getPixel(5, 5)
        ImageCleanupProcessor.fixLighting(input)
        assertEquals("fixLighting must not modify original", pixelBefore, input.getPixel(5, 5))
    }

    // ── applyFilters (non-destructive) ────────────────────────────────────────

    @Test
    fun `applyFilters with all filters returns new bitmap without modifying source`() {
        val input = noisyBitmap()
        val pixelBefore = input.getPixel(5, 5)

        val result = ImageCleanupProcessor.applyFilters(
            input,
            setOf(
                NonDestructiveFilter.DENOISE,
                NonDestructiveFilter.SHARPEN,
                NonDestructiveFilter.FIX_LIGHTING,
            ),
        )

        assertEquals("source must not be modified", pixelBefore, input.getPixel(5, 5))
        assertNotEquals("result should be a different object from input", input, result)
    }

    @Test
    fun `applyFilters with empty set returns copy without modifying source`() {
        val input = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.BLUE)
        val result = ImageCleanupProcessor.applyFilters(input, emptySet())
        assertEquals("result must have same pixel as input for empty filter set",
            Color.BLUE, result.getPixel(5, 5))
    }

    @Test
    fun `applyFilters DENOISE only — result differs from input`() {
        val input = noisyBitmap()
        val result = ImageCleanupProcessor.applyFilters(input, setOf(NonDestructiveFilter.DENOISE))
        val diff = pixelDifference(input, result)
        assertNotEquals("DENOISE filter should change pixels", 0, diff)
    }

    @Test
    fun `applyFilters SHARPEN only — result differs from input`() {
        val input = noisyBitmap()
        val result = ImageCleanupProcessor.applyFilters(input, setOf(NonDestructiveFilter.SHARPEN))
        val diff = pixelDifference(input, result)
        assertNotEquals("SHARPEN filter should change pixels", 0, diff)
    }

    @Test
    fun `applyFilters FIX_LIGHTING only — result differs from input`() {
        val input = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.rgb(80, 80, 80))
        val result = ImageCleanupProcessor.applyFilters(input, setOf(NonDestructiveFilter.FIX_LIGHTING))
        val diff = pixelDifference(input, result)
        assertNotEquals("FIX_LIGHTING filter should change pixels", 0, diff)
    }

    @Test
    fun `all NonDestructiveFilter values can be applied without crash`() {
        val input = noisyBitmap()
        NonDestructiveFilter.entries.forEach { filter ->
            val result = ImageCleanupProcessor.applyFilters(input, setOf(filter))
            assertEquals("output must have same dimensions", input.width,  result.width)
            assertEquals("output must have same dimensions", input.height, result.height)
        }
    }
}
