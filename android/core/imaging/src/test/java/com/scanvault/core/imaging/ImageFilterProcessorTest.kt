package com.scanvault.core.imaging

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ImageFilterProcessor] — image filters (2B.6).
 *
 * These tests run under Robolectric. Important limitation:
 * Robolectric's Canvas stub does NOT process [ColorMatrixColorFilter] at the
 * pixel level, so ColorMatrix-based filters (AUTO, MAGIC_COLOR, GRAYSCALE)
 * produce a new bitmap but the pixel values are not transformed.
 *
 * Tests in this file therefore verify:
 *  - ORIGINAL returns the exact same (unmodified) bitmap instance (zero-copy).
 *  - Every non-ORIGINAL filter returns a NEW bitmap (not the source object).
 *  - All filters return output whose size matches the source.
 *  - BLACK_AND_WHITE correctly produces only black/white pixels (uses
 *    getPixels/setPixels, which Robolectric fully supports).
 *  - [ImageFilter.fromKey] resolves every key correctly.
 *
 * Full pixel-level verification of ColorMatrix filters is handled by
 * instrumented tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImageFilterProcessorTest {

    // Source bitmap: 8×8, contains distinct non-zero pixels
    private val source: Bitmap by lazy {
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also { bmp ->
            // Fill with a gradient so B&W has both black and white pixels
            for (x in 0 until 8) {
                for (y in 0 until 8) {
                    val lum = (x + y) * 16 // 0..224
                    bmp.setPixel(x, y, Color.argb(255, lum, lum / 2, 255 - lum))
                }
            }
        }
    }

    // ── ORIGINAL: zero-copy ───────────────────────────────────────────────────

    @Test
    fun original_returnsExactSameBitmapInstance() {
        val result = ImageFilterProcessor.apply(source, ImageFilter.ORIGINAL)
        assertSame("ORIGINAL must be zero-copy — same instance", source, result)
    }

    // ── All non-ORIGINAL filters return a NEW bitmap ──────────────────────────

    @Test
    fun auto_returnsNewBitmap_notSameReference() {
        val result = ImageFilterProcessor.apply(source, ImageFilter.AUTO)
        assertNotSame("AUTO must return a new bitmap", source, result)
    }

    @Test
    fun magicColor_returnsNewBitmap_notSameReference() {
        val result = ImageFilterProcessor.apply(source, ImageFilter.MAGIC_COLOR)
        assertNotSame("MAGIC_COLOR must return a new bitmap", source, result)
    }

    @Test
    fun grayscale_returnsNewBitmap_notSameReference() {
        val result = ImageFilterProcessor.apply(source, ImageFilter.GRAYSCALE)
        assertNotSame("GRAYSCALE must return a new bitmap", source, result)
    }

    @Test
    fun blackAndWhite_returnsNewBitmap_notSameReference() {
        val result = ImageFilterProcessor.apply(source, ImageFilter.BLACK_AND_WHITE)
        assertNotSame("BLACK_AND_WHITE must return a new bitmap", source, result)
    }

    // ── Output size matches source for all filters ────────────────────────────

    @Test
    fun allFilters_outputWidthMatchesSource() {
        for (filter in ImageFilter.entries) {
            val result = ImageFilterProcessor.apply(source, filter)
            assertEquals("Width mismatch for $filter", source.width, result.width)
        }
    }

    @Test
    fun allFilters_outputHeightMatchesSource() {
        for (filter in ImageFilter.entries) {
            val result = ImageFilterProcessor.apply(source, filter)
            assertEquals("Height mismatch for $filter", source.height, result.height)
        }
    }

    // ── BLACK_AND_WHITE: pixel-level verification (getPixels/setPixels) ───────

    @Test
    fun blackAndWhite_pixelsAreOnlyBlackOrWhite() {
        // Use a bitmap with known non-neutral pixels so both black and white appear
        val bwSource = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).also { bmp ->
            // Top half: bright pixels (should become white)
            for (x in 0 until 4) {
                bmp.setPixel(x, 0, Color.argb(255, 220, 220, 220))
                bmp.setPixel(x, 1, Color.argb(255, 200, 200, 200))
            }
            // Bottom half: dark pixels (should become black)
            for (x in 0 until 4) {
                bmp.setPixel(x, 2, Color.argb(255, 40, 40, 40))
                bmp.setPixel(x, 3, Color.argb(255, 20, 20, 20))
            }
        }

        val result = ImageFilterProcessor.apply(bwSource, ImageFilter.BLACK_AND_WHITE)

        for (x in 0 until result.width) {
            for (y in 0 until result.height) {
                val px = result.getPixel(x, y)
                assertTrue(
                    "Pixel ($x,$y) should be black or white, got $px",
                    px == Color.BLACK || px == Color.WHITE,
                )
            }
        }
    }

    @Test
    fun blackAndWhite_hasBothBlackAndWhitePixels() {
        val bwSource = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).also { bmp ->
            for (x in 0 until 4) {
                bmp.setPixel(x, 0, Color.argb(255, 230, 230, 230)) // bright → white
                bmp.setPixel(x, 3, Color.argb(255, 10, 10, 10))   // dark → black
            }
            // Middle rows: medium values
            for (x in 0 until 4) {
                bmp.setPixel(x, 1, Color.argb(255, 150, 150, 150))
                bmp.setPixel(x, 2, Color.argb(255, 80, 80, 80))
            }
        }

        val result = ImageFilterProcessor.apply(bwSource, ImageFilter.BLACK_AND_WHITE)
        val pixels = (0 until result.width).flatMap { x ->
            (0 until result.height).map { y -> result.getPixel(x, y) }
        }

        assertTrue("B&W output should have white pixels", pixels.any { it == Color.WHITE })
        assertTrue("B&W output should have black pixels", pixels.any { it == Color.BLACK })
    }

    @Test
    fun blackAndWhite_outputDiffersFromSource() {
        // Source has non-B&W pixels; B&W output should differ
        val src = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.setPixel(0, 0, Color.argb(255, 200, 100, 50)) // non-B&W
            bmp.setPixel(1, 0, Color.argb(255, 50, 200, 100)) // non-B&W
            bmp.setPixel(0, 1, Color.argb(255, 100, 50, 200)) // non-B&W
            bmp.setPixel(1, 1, Color.argb(255, 10, 10, 10))   // near-black
        }
        val result = ImageFilterProcessor.apply(src, ImageFilter.BLACK_AND_WHITE)

        // At least one pixel must differ (colour pixels become black or white)
        val differs = (0 until src.width).any { x ->
            (0 until src.height).any { y -> src.getPixel(x, y) != result.getPixel(x, y) }
        }
        assertTrue("B&W output should differ from source", differs)
    }

    // ── ImageFilter.fromKey ───────────────────────────────────────────────────

    @Test
    fun fromKey_resolvesOriginal() {
        assertEquals(ImageFilter.ORIGINAL, ImageFilter.fromKey("original"))
    }

    @Test
    fun fromKey_resolvesAuto() {
        assertEquals(ImageFilter.AUTO, ImageFilter.fromKey("auto"))
    }

    @Test
    fun fromKey_resolvesMagicColor() {
        assertEquals(ImageFilter.MAGIC_COLOR, ImageFilter.fromKey("magic_color"))
    }

    @Test
    fun fromKey_resolvesGrayscale() {
        assertEquals(ImageFilter.GRAYSCALE, ImageFilter.fromKey("grayscale"))
    }

    @Test
    fun fromKey_resolvesBlackAndWhite() {
        assertEquals(ImageFilter.BLACK_AND_WHITE, ImageFilter.fromKey("bw"))
    }

    @Test
    fun fromKey_unknownKey_defaultsToOriginal() {
        assertEquals(ImageFilter.ORIGINAL, ImageFilter.fromKey("nonexistent_filter"))
    }

    @Test
    fun fromKey_emptyKey_defaultsToOriginal() {
        assertEquals(ImageFilter.ORIGINAL, ImageFilter.fromKey(""))
    }

    // ── ImageFilter enum properties ───────────────────────────────────────────

    @Test
    fun allFilters_haveUniqueKeys() {
        val keys = ImageFilter.entries.map { it.key }
        assertEquals("All filter keys must be unique", keys.size, keys.toSet().size)
    }

    @Test
    fun allFilters_haveNonBlankLabels() {
        for (filter in ImageFilter.entries) {
            assertFalse("Filter $filter has blank label", filter.label.isBlank())
        }
    }

    @Test
    fun fiveFiltersExist() {
        assertEquals(5, ImageFilter.entries.size)
    }
}
