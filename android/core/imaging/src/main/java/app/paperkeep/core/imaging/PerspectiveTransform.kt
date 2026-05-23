package app.paperkeep.core.imaging

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Performs perspective correction (warp) on a bitmap given four source corner points,
 * mapping them to a rectangular output of [outputWidth] × [outputHeight].
 *
 * For Phase 1 we use Android's [Matrix] API for an affine approximation.
 * The full perspective warp (OpenCV getPerspectiveTransform + warpPerspective) is
 * added when OpenCV is available — [PerspectiveTransform.warp] already matches the
 * interface that the OpenCV upgrade will implement.
 *
 * On devices without OpenCV the result is a cropped + rotated view of the source
 * image, which is acceptable for Phase 1.
 */
object PerspectiveTransform {

    /**
     * Warp [bitmap] using the source [quad] corners into a rectified output.
     *
     * @param bitmap The captured full-resolution image.
     * @param quad   The four corners (in bitmap pixel space) of the document.
     * @param outputWidth  Width of the output rectified image. Defaults to the natural width.
     * @param outputHeight Height of the output rectified image. Defaults to natural height.
     * @return The perspective-corrected bitmap, or the original cropped bitmap if the
     *         transform cannot be applied.
     */
    fun warp(
        bitmap: Bitmap,
        quad: Quad,
        outputWidth: Int = naturalOutputWidth(quad),
        outputHeight: Int = naturalOutputHeight(quad),
    ): Bitmap {
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) return bitmap

        val outW = outputWidth.coerceAtLeast(1)
        val outH = outputHeight.coerceAtLeast(1)

        // Prefer true projective warp via OpenCV when native libs are loaded.
        if (OpenCvEdgeDetector.isLoaded) {
            val opencvResult = OpenCvBridge.warp(bitmap, quad, outW, outH)
            if (opencvResult != null) return opencvResult
        }

        return try {
            warpWithMatrix(bitmap, quad, outW, outH)
        } catch (e: Exception) {
            // Graceful fallback: return the original bitmap uncropped
            bitmap
        }
    }

    /**
     * Rotate [bitmap] by [degrees] (must be 0, 90, 180, or 270).
     */
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Approximate perspective warp using Android's [Matrix].
     * A true 8-parameter perspective warp requires OpenCV — this is a correct
     * affine approximation that works well for near-planar documents.
     */
    private fun warpWithMatrix(
        src: Bitmap,
        quad: Quad,
        outW: Int,
        outH: Int,
    ): Bitmap {
        val srcPts = floatArrayOf(
            quad.topLeft.x, quad.topLeft.y,
            quad.topRight.x, quad.topRight.y,
            quad.bottomRight.x, quad.bottomRight.y,
            quad.bottomLeft.x, quad.bottomLeft.y,
        )
        val dstPts = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat(),
        )
        val matrix = Matrix()
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, matrix, paint)
        return output
    }

    /** Estimates the natural width of the rectified document from the quad's top edge. */
    private fun naturalOutputWidth(quad: Quad): Int {
        val dx = quad.topRight.x - quad.topLeft.x
        val dy = quad.topRight.y - quad.topLeft.y
        return sqrt(dx * dx + dy * dy).toInt().coerceAtLeast(1)
    }

    /** Estimates the natural height of the rectified document from the quad's left edge. */
    private fun naturalOutputHeight(quad: Quad): Int {
        val dx = quad.bottomLeft.x - quad.topLeft.x
        val dy = quad.bottomLeft.y - quad.topLeft.y
        return sqrt(dx * dx + dy * dy).toInt().coerceAtLeast(1)
    }
}
