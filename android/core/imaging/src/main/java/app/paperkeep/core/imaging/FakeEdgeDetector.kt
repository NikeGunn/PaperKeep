package app.paperkeep.core.imaging

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Deterministic edge detector for unit tests.
 *
 * Behaviour:
 *  - If the bitmap is a solid white/uniform image → returns [DetectionResult.NotFound]
 *  - Otherwise → returns a [DetectionResult.Found] with a quad that covers 80% of the image
 *
 * This lets tests exercise the full state machine without loading OpenCV native libs.
 */
class FakeEdgeDetector : EdgeDetector {

    override fun detect(bitmap: Bitmap): DetectionResult {
        if (isUniform(bitmap)) return DetectionResult.NotFound

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val margin = 0.1f
        val quad = Quad(
            topLeft = Point2f(w * margin, h * margin),
            topRight = Point2f(w * (1 - margin), h * margin),
            bottomRight = Point2f(w * (1 - margin), h * (1 - margin)),
            bottomLeft = Point2f(w * margin, h * (1 - margin)),
        )
        return DetectionResult.Found(quad)
    }

    /** Returns true if every sampled pixel has the same luminance (±5). */
    private fun isUniform(bitmap: Bitmap): Boolean {
        if (bitmap.width == 0 || bitmap.height == 0) return true

        val samples = mutableListOf<Int>()
        val stepX = maxOf(1, bitmap.width / 8)
        val stepY = maxOf(1, bitmap.height / 8)
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                samples.add(Color.red(pixel) + Color.green(pixel) + Color.blue(pixel))
            }
        }
        val min = samples.min()
        val max = samples.max()
        return (max - min) < 15
    }
}
