package app.paperkeep.core.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader

/**
 * Non-destructive image cleanup filters (3B.9).
 *
 * All operations return **new** bitmaps — the [source] passed in is never
 * modified. This makes it safe to hold the original in memory and re-apply
 * a different filter set without re-capturing.
 */
object ImageCleanupProcessor {

    /**
     * Apply the given set of [filters] to [source] in a defined order:
     * DENOISE → SHARPEN → FIX_LIGHTING.
     *
     * @return A new bitmap with the requested filters applied.
     *         If [filters] is empty, a copy of [source] is returned.
     */
    fun applyFilters(source: Bitmap, filters: Set<NonDestructiveFilter>): Bitmap {
        var result = source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)

        if (NonDestructiveFilter.DENOISE in filters) result = denoise(result)
        if (NonDestructiveFilter.SHARPEN in filters) result = sharpen(result)
        if (NonDestructiveFilter.FIX_LIGHTING in filters) result = fixLighting(result)

        return result
    }

    // ── Filter implementations ─────────────────────────────────────────────

    /**
     * Denoise: 3×3 box-blur convolution (pure Bitmap pixel operations).
     *
     * Each output pixel is the arithmetic mean of itself and its 8 neighbours.
     * Edge pixels copy their nearest valid neighbour.
     */
    fun denoise(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val input = IntArray(w * h)
        source.getPixels(input, 0, w, 0, 0, w, h)
        val output = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var count = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val px = input[ny * w + nx]
                        r += Color.red(px)
                        g += Color.green(px)
                        b += Color.blue(px)
                        count++
                    }
                }
                output[y * w + x] = Color.rgb(r / count, g / count, b / count)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(output, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Sharpen: unsharp-mask (subtract blurred from original, then add back).
     *
     * result = clamp(original + (original - blurred) * amount)
     */
    fun sharpen(source: Bitmap): Bitmap {
        val blurred = denoise(source)
        val w = source.width
        val h = source.height

        val orig = IntArray(w * h)
        val blur = IntArray(w * h)
        source.getPixels(orig, 0, w, 0, 0, w, h)
        blurred.getPixels(blur, 0, w, 0, 0, w, h)

        val amount = 1.0f
        val output = IntArray(w * h)

        for (i in orig.indices) {
            val r = (Color.red(orig[i]) + (Color.red(orig[i]) - Color.red(blur[i])) * amount)
                .toInt().coerceIn(0, 255)
            val g = (Color.green(orig[i]) + (Color.green(orig[i]) - Color.green(blur[i])) * amount)
                .toInt().coerceIn(0, 255)
            val b = (Color.blue(orig[i]) + (Color.blue(orig[i]) - Color.blue(blur[i])) * amount)
                .toInt().coerceIn(0, 255)
            output[i] = Color.rgb(r, g, b)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(output, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Remove uneven illumination (hand shadows, vignette, lopsided desk lamp)
     * so paper reads uniformly bright across the whole page. This is the
     * "clean document" step that makes scans look government-grade.
     *
     * Delegates to OpenCV's illumination-normalisation pipeline when native
     * libs are loaded. On JVM unit tests (no OpenCV) returns null so callers
     * can fall back to the raw bitmap.
     */
    fun removeShadow(source: Bitmap): Bitmap? {
        if (!OpenCvEdgeDetector.isLoaded) return null
        return OpenCvBridge.removeShadow(source)
    }

    /**
     * Fix lighting: brightness +20, contrast ×1.2 via direct pixel manipulation.
     */
    fun fixLighting(source: Bitmap): Bitmap {
        val contrast = 1.2f
        val brightness = 20f
        val translate = ((-0.5f * contrast + 0.5f) * 255f) + brightness
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val output = IntArray(w * h)
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (Color.red(px) * contrast + translate).toInt().coerceIn(0, 255)
            val g = (Color.green(px) * contrast + translate).toInt().coerceIn(0, 255)
            val b = (Color.blue(px) * contrast + translate).toInt().coerceIn(0, 255)
            output[i] = Color.argb(Color.alpha(px), r, g, b)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(output, 0, w, 0, 0, w, h)
        return result
    }
}

/** Filters that can be applied non-destructively via [ImageCleanupProcessor.applyFilters]. */
enum class NonDestructiveFilter {
    DENOISE,
    SHARPEN,
    FIX_LIGHTING,
}
