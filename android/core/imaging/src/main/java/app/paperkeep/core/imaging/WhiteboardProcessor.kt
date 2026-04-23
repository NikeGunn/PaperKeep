package app.paperkeep.core.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Whiteboard image enhancement processor (3B.4).
 *
 * - Glare removal: boost brightness and contrast of the image so dark marks
 *   stand out against the whiteboard background.
 * - Marker colour boost: increase HSV saturation by 1.5× to make marker
 *   strokes more vivid.
 *
 * All operations are pure Android Bitmap / Canvas — no native OpenCV needed.
 */
object WhiteboardProcessor {

    /**
     * Apply glare removal to [source].
     *
     * Increases brightness (+30) and contrast (×1.3) using direct pixel manipulation.
     * Returns a new ARGB_8888 bitmap; [source] is not modified.
     */
    fun removeGlare(source: Bitmap): Bitmap {
        val contrast = 1.3f
        val brightness = 30f
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val translate = ((-0.5f * contrast + 0.5f) * 255f) + brightness
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (Color.red(px) * contrast + translate).toInt().coerceIn(0, 255)
            val g = (Color.green(px) * contrast + translate).toInt().coerceIn(0, 255)
            val b = (Color.blue(px) * contrast + translate).toInt().coerceIn(0, 255)
            pixels[i] = Color.argb(Color.alpha(px), r, g, b)
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, w, 0, 0, w, h)
        return output
    }

    /**
     * Boost marker colour saturation in [source] by a factor of 1.5×.
     *
     * Uses [ColorMatrix.setSaturation] which blends the identity matrix with
     * the grayscale matrix — the standard Android approach.
     * Returns a new bitmap; [source] is not modified.
     */
    fun boostMarkerColor(source: Bitmap): Bitmap {
        val matrix = ColorMatrix().apply { setSaturation(1.5f) }
        return applyColorMatrix(source, matrix)
    }

    /**
     * Full whiteboard enhancement pipeline: glare removal then colour boost.
     */
    fun process(source: Bitmap): Bitmap = boostMarkerColor(removeGlare(source))

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

    /**
     * Compute the average HSV saturation across all pixels in [bitmap].
     * Useful for test assertions.
     */
    fun averageSaturation(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        if (pixels.isEmpty()) return 0f
        val hsv = FloatArray(3)
        var total = 0.0
        for (px in pixels) {
            Color.colorToHSV(px, hsv)
            total += hsv[1]
        }
        return (total / pixels.size).toFloat()
    }
}
