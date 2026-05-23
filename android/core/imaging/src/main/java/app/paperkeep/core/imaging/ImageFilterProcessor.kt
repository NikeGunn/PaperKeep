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
        ImageFilter.DOCUMENT -> applyDocument(source)
        ImageFilter.LIGHTEN -> applyLighten(source)
        ImageFilter.VIVID -> applyVivid(source)
        ImageFilter.WHITEBOARD -> applyWhiteboard(source)
        ImageFilter.SEPIA -> applySepia(source)
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
     * Magic Color: prefer the real OpenCV pipeline (CLAHE on L channel +
     * gamma) when native libs are available — it produces the CamScanner-grade
     * "paper white + sharp ink" look. Falls back to the ColorMatrix-based
     * saturation/contrast boost on JVM unit tests.
     */
    private fun applyMagicColor(source: Bitmap): Bitmap {
        if (OpenCvEdgeDetector.isLoaded) {
            val opencvResult = OpenCvBridge.magicColor(source)
            if (opencvResult != null) return opencvResult
        }
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

    /**
     * Document: CamScanner-style "Enhanced Document" look — slight desaturation
     * (0.85), strong contrast (×1.45), and a brightness lift (+18) so paper
     * reads clean white and ink stays crisp black. The single most-used filter
     * in mainstream scanner apps.
     */
    private fun applyDocument(source: Bitmap): Bitmap {
        val sat = ColorMatrix().apply { setSaturation(0.85f) }
        val contrast = 1.45f
        val brightness = 18f
        val translate = ((-0.5f * contrast + 0.5f) * 255f) + brightness
        val cc = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        sat.postConcat(cc)
        return applyColorMatrix(source, sat)
    }

    /**
     * Lighten: brightness lift without blowing out whites. +35 brightness with
     * a gentle 1.05× contrast curve so the highlights compress instead of
     * clipping. Aimed at faded ink, carbon copies, dim-room captures.
     */
    private fun applyLighten(source: Bitmap): Bitmap {
        val contrast = 1.05f
        val brightness = 35f
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
     * Vivid: saturation ×1.7 + contrast ×1.2. Pops coloured diagrams,
     * sticky-notes, art-class assignments, anything where colour fidelity
     * matters more than paper-white neutrality.
     */
    private fun applyVivid(source: Bitmap): Bitmap {
        val sat = ColorMatrix().apply { setSaturation(1.7f) }
        val contrast = 1.20f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val cc = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        sat.postConcat(cc)
        return applyColorMatrix(source, sat)
    }

    /**
     * Whiteboard: aggressive brightness + contrast that pushes off-white
     * background to pure white while preserving marker colours. Same effect
     * Office Lens and Google Drive Scan apply to whiteboard captures.
     *
     * Algorithm: brightness +45, contrast ×1.55, saturation ×1.15 to keep
     * marker hues from washing out under the brightness lift.
     */
    private fun applyWhiteboard(source: Bitmap): Bitmap {
        val sat = ColorMatrix().apply { setSaturation(1.15f) }
        val contrast = 1.55f
        val brightness = 45f
        val translate = ((-0.5f * contrast + 0.5f) * 255f) + brightness
        val cc = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        sat.postConcat(cc)
        return applyColorMatrix(source, sat)
    }

    /**
     * Sepia: classic warm-brown vintage tint. Desaturate first, then apply
     * the well-known Microsoft sepia matrix (R,G,B weights tuned to land on
     * warm browns). Useful for letters and archival documents.
     */
    private fun applySepia(source: Bitmap): Bitmap {
        val sat = ColorMatrix().apply { setSaturation(0f) }
        val sepia = ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f,     0f,     0f,     1f, 0f,
            )
        )
        sat.postConcat(sepia)
        return applyColorMatrix(source, sat)
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
