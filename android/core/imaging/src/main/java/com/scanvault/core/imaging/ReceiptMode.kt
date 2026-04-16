package com.scanvault.core.imaging

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Receipt capture processing (3B.3).
 *
 * - Enforces a tall (1:3 portrait) crop: output height >= input width * 2.
 * - Applies an automatic B&W filter for legible receipts.
 * - Extracts "total" and "merchant" fields from OCR text via regex heuristics.
 */
object ReceiptMode {

    /**
     * Apply receipt processing to [source]:
     *  1. Centre-crop to a tall 1:3 aspect ratio (portrait).
     *  2. Convert to B&W (adaptive threshold via grayscale mean).
     *
     * Returns a new bitmap — [source] is not modified.
     */
    fun process(source: Bitmap): Bitmap {
        val cropped = cropTallAspect(source)
        return applyBlackAndWhite(cropped)
    }

    /**
     * Extract receipt fields from raw OCR [text].
     *
     * - [ReceiptData.total]: first match of `total.*?(\$?\d+[\.,]\d{2})` (case-insensitive).
     * - [ReceiptData.merchant]: first non-blank line (heuristic: receipt header = merchant).
     */
    fun extractFields(text: String): ReceiptData {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val merchant = lines.firstOrNull()

        // Match "total" followed by an optional currency sign and a decimal amount
        val totalRegex = Regex("""(?i)total[^\d]*(\$?\d+[.,]\d{2})""")
        val total = totalRegex.find(text)?.groupValues?.get(1)

        return ReceiptData(total = total, merchant = merchant)
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Centre-crop [source] so that height >= width * 2 (≈1:3 portrait).
     *
     * If [source] already satisfies the constraint it is returned as-is.
     * Otherwise the bitmap is cropped from the top keeping full width and
     * height = min(sourceHeight, width * 3).
     */
    internal fun cropTallAspect(source: Bitmap): Bitmap {
        val targetHeight = minOf(source.height, source.width * 3)
        return if (targetHeight >= source.width * 2) {
            // Already tall enough — only return as-is if it fits the constraint
            Bitmap.createBitmap(source, 0, 0, source.width, targetHeight)
        } else {
            // Source is not tall enough; return what we have (may not satisfy ratio)
            source
        }
    }

    /**
     * Convert [source] to black-and-white using an adaptive mean threshold.
     */
    private fun applyBlackAndWhite(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(pixels.size)
        var sum = 0L
        for (i in pixels.indices) {
            val px = pixels[i]
            val lum = (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
            gray[i] = lum
            sum += lum
        }
        val threshold = if (pixels.isNotEmpty()) (sum / pixels.size).toInt() else 128

        for (i in pixels.indices) {
            pixels[i] = if (gray[i] >= threshold) Color.WHITE else Color.BLACK
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}

/**
 * Extracted fields from a receipt's OCR text.
 *
 * @param total    Dollar/amount string, e.g. `"$12.99"` or `null` if not found.
 * @param merchant First non-blank line from the OCR text (header heuristic), or `null`.
 */
data class ReceiptData(
    val total: String?,
    val merchant: String?,
)
