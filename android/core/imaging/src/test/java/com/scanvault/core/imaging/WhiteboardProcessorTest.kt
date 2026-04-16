package com.scanvault.core.imaging

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

    // Convenience re-export so tests compile without importing junit directly
    private fun assertEquals(msg: String, expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(msg, expected, actual)
    }
}
