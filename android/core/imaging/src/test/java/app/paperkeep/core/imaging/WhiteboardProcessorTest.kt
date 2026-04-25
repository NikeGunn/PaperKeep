package app.paperkeep.core.imaging

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class WhiteboardProcessorTest {

    /** Create a 10×10 bitmap filled with the given ARGB [color]. */
    private fun solidBitmap(color: Int, w: Int = 10, h: Int = 10): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    @Test
    fun `removeGlare changes pixel values compared to input`() {
        // Dark grey input — glare removal should brighten it
        val input = solidBitmap(Color.rgb(80, 80, 80))
        val output = WhiteboardProcessor.removeGlare(input)

        // At least one pixel must differ
        val inPx = input.getPixel(5, 5)
        val outPx = output.getPixel(5, 5)
        assertNotEquals(
            "removeGlare output pixel should differ from input",
            inPx,
            outPx,
        )
    }

    @Test
    fun `removeGlare does not modify original bitmap`() {
        val input = solidBitmap(Color.rgb(80, 80, 80))
        val pixelBefore = input.getPixel(5, 5)
        WhiteboardProcessor.removeGlare(input)
        assertEquals("original bitmap must not be modified", pixelBefore, input.getPixel(5, 5))
    }

    @Test
    fun `boostMarkerColor increases average saturation`() {
        // A medium-saturation coloured bitmap (some red content)
        val input = solidBitmap(Color.rgb(200, 80, 80))
        val satBefore = WhiteboardProcessor.averageSaturation(input)
        val output = WhiteboardProcessor.boostMarkerColor(input)
        val satAfter = WhiteboardProcessor.averageSaturation(output)

        assertTrue(
            "saturation after boost ($satAfter) should be >= saturation before ($satBefore)",
            satAfter >= satBefore,
        )
    }

    @Test
    fun `boostMarkerColor does not modify original bitmap`() {
        val input = solidBitmap(Color.rgb(200, 80, 80))
        val pixelBefore = input.getPixel(5, 5)
        WhiteboardProcessor.boostMarkerColor(input)
        assertEquals("original bitmap must not be modified", pixelBefore, input.getPixel(5, 5))
    }

    // ── removeHandShadow ──────────────────────────────────────────────────────

    @Test
    fun `removeHandShadow brightens mid-tone grey pixels`() {
        // Mid-tone grey (lum ≈ 0.33) with near-zero saturation → shadow region
        val grey = Color.rgb(85, 85, 85)
        val input = solidBitmap(grey)
        val output = WhiteboardProcessor.removeHandShadow(input)
        val inLum  = luminance(input.getPixel(5, 5))
        val outLum = luminance(output.getPixel(5, 5))
        assertTrue("shadow pixel should be brightened: $inLum → $outLum", outLum > inLum)
    }

    @Test
    fun `removeHandShadow does not modify vivid marker colour`() {
        // Saturated red — marker stroke should be left intact
        val red = Color.rgb(220, 20, 20)
        val input = solidBitmap(red)
        val inPx  = input.getPixel(5, 5)
        val output = WhiteboardProcessor.removeHandShadow(input)
        val outPx  = output.getPixel(5, 5)
        assertEquals("vivid marker pixel must not be altered", inPx, outPx)
    }

    @Test
    fun `removeHandShadow does not modify pure white`() {
        val input = solidBitmap(Color.WHITE)
        val inPx  = input.getPixel(5, 5)
        val output = WhiteboardProcessor.removeHandShadow(input)
        assertEquals("white pixel must not be altered", inPx, output.getPixel(5, 5))
    }

    @Test
    fun `removeHandShadow does not modify original bitmap`() {
        val input = solidBitmap(Color.rgb(85, 85, 85))
        val before = input.getPixel(5, 5)
        WhiteboardProcessor.removeHandShadow(input)
        assertEquals("original must not be modified", before, input.getPixel(5, 5))
    }

    @Test
    fun `process pipeline produces valid bitmap`() {
        val input = solidBitmap(Color.rgb(150, 150, 150), w = 50, h = 50)
        val output = WhiteboardProcessor.process(input)
        assertTrue(output.width > 0 && output.height > 0)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun luminance(color: Int): Float =
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 255000f

    // Convenience re-export so tests compile without importing junit directly
    private fun assertEquals(msg: String, expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(msg, expected, actual)
    }
}
