package app.paperkeep.core.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Applies one of the five [ImageFilter] variants to a [Bitmap] (2B.6).
 *
 * All operations are pure Android framework — no OpenCV dependency needed.
 * Processing is CPU-bound; call from a background dispatcher.
 */
object ImageFilterProcessor {

    /**
     * Apply [filter] to [source] and return the result bitmap.
     *
     * [ORIGINAL] returns [source] unchanged (zero-copy).
     * All other filters return a new ARGB_8888 bitmap.
     */
    fun apply(source: Bitmap, filter: ImageFilter): Bitmap = when (filter) {
        ImageFilter.ORIGINAL -> source
        ImageFilter.AUTO -> applyAuto(source)
        ImageFilter.MAGIC_COLOR -> applyMagicColor(source)
        ImageFilter.GRAYSCALE -> applyGrayscale(source)
        ImageFilter.BLACK_AND_WHITE -> applyBlackAndWhite(source)
    }

    // ── Filter implementations ─────────────────────────────────────────────────

    /**
     * Auto-enhance: boost contrast and lightly push brightness.
     *
     * Uses a [ColorMatrix] with:
     *  - Contrast scale  ×1.25
     *  - Brightness offset +10 (range −255…+255)
     */
    private fun applyAuto(source: Bitmap): Bitmap {
        val contrast = 1.25f
        val brightness = 10f
        val translate = ((-0.5f * contrast + 0.5f) * 255f) + brightness
        val matrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        return applyColorMatrix(source, matrix)
    }

    /**
     * Magic Color: boost saturation ×1.6 and contrast ×1.15.
     *
     * Saturation boost is applied by blending the identity matrix with the
     * grayscale matrix at the desired ratio, which is exactly how Android's
     * [ColorMatrix.setSaturation] works under the hood.
     */
    private fun applyMagicColor(source: Bitmap): Bitmap {
        val saturationMatrix = ColorMatrix().apply { setSaturation(1.6f) }
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                1.15f, 0f, 0f, 0f, -18.75f,
                0f, 1.15f, 0f, 0f, -18.75f,
                0f, 0f, 1.15f, 0f, -18.75f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        // Apply contrast first, then saturation
        contrastMatrix.postConcat(saturationMatrix)
        return applyColorMatrix(source, contrastMatrix)
    }

    /**
     * Grayscale: convert RGB to luminance using BT.601 weights.
     *
     * We could use [ColorMatrix.setSaturation(0f)] but explicit weights keep
     * the behaviour stable and testable.
     */
    private fun applyGrayscale(source: Bitmap): Bitmap {
        val r = 0.299f; val g = 0.587f; val b = 0.114f
        val matrix = ColorMatrix(
            floatArrayOf(
                r, g, b, 0f, 0f,
                r, g, b, 0f, 0f,
                r, g, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        return applyColorMatrix(source, matrix)
    }

    /**
     * Black & White: adaptive threshold binarisation.
     *
     * Algorithm:
     *  1. Convert to grayscale in a pixel array scan.
     *  2. Compute mean luminance as the adaptive threshold.
     *  3. Set each pixel to black (below threshold) or white (at or above).
     *
     * For large documents this is faster than the ColorMatrix approach for
     * binarisation because we skip intermediate float calculations.
     */
    private fun applyBlackAndWhite(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert to grayscale and compute mean for adaptive threshold
        val gray = IntArray(pixels.size)
        var sum = 0L
        for (i in pixels.indices) {
            val px = pixels[i]
            val lum = (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
            gray[i] = lum
            sum += lum
        }
        val threshold = (sum / pixels.size).toInt()

        // Binarise
        for (i in pixels.indices) {
            pixels[i] = if (gray[i] >= threshold) Color.WHITE else Color.BLACK
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun applyColorMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }
}
