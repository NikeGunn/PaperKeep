package app.paperkeep.core.imaging

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production edge detector.
 *
 * The original OpenCV integration is intentionally replaced here with a fully
 * on-device Kotlin fallback so edge detection is never a no-op when native
 * OpenCV binaries are unavailable.
 *
 * Detection approach:
 *  1. Downsample large frames for throughput.
 *  2. Estimate background luminance from border samples.
 *  3. Build foreground runs row-by-row using adaptive luminance distance.
 *  4. Fit a 4-corner quad from top/bottom row bands.
 *  5. Reject tiny/degenerate quads.
 */
@Singleton
class OpenCvEdgeDetector @Inject constructor() : EdgeDetector {

    override fun detect(bitmap: Bitmap): DetectionResult {
        if (bitmap.isRecycled || bitmap.width < 2 || bitmap.height < 2) {
            return DetectionResult.NotFound
        }

        if (isOpenCvLoaded) {
            val nativeQuad = detectQuadNative(bitmap)
            if (nativeQuad != null && nativeQuad.isValid(MIN_QUAD_AREA_FRACTION, bitmap.width, bitmap.height)) {
                return DetectionResult.Found(nativeQuad)
            }
        }

        val analysisBitmap = downsampleForAnalysis(bitmap)
        val analysisQuad = detectQuadHeuristic(analysisBitmap) ?: return DetectionResult.NotFound

        val scaleX = bitmap.width.toFloat() / analysisBitmap.width.toFloat()
        val scaleY = bitmap.height.toFloat() / analysisBitmap.height.toFloat()
        val mapped = Quad(
            topLeft = Point2f(analysisQuad.topLeft.x * scaleX, analysisQuad.topLeft.y * scaleY),
            topRight = Point2f(analysisQuad.topRight.x * scaleX, analysisQuad.topRight.y * scaleY),
            bottomRight = Point2f(analysisQuad.bottomRight.x * scaleX, analysisQuad.bottomRight.y * scaleY),
            bottomLeft = Point2f(analysisQuad.bottomLeft.x * scaleX, analysisQuad.bottomLeft.y * scaleY),
        )

        return if (mapped.isValid(MIN_QUAD_AREA_FRACTION, bitmap.width, bitmap.height)) {
            DetectionResult.Found(mapped)
        } else {
            DetectionResult.NotFound
        }
    }

    private fun downsampleForAnalysis(bitmap: Bitmap): Bitmap {
        val maxEdge = maxOf(bitmap.width, bitmap.height)
        if (maxEdge <= MAX_ANALYSIS_EDGE) return bitmap

        val scale = MAX_ANALYSIS_EDGE.toFloat() / maxEdge.toFloat()
        val outW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val outH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, outW, outH, true)
    }

    private fun detectQuadHeuristic(bitmap: Bitmap): Quad? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val borderLuma = sampleBorderLuminance(pixels, width, height)
        if (borderLuma.isEmpty()) return null

        val background = median(borderLuma)
        val stdDev = standardDeviation(borderLuma, background)
        val threshold = maxOf(MIN_LUMA_THRESHOLD, stdDev * ADAPTIVE_STDDEV_MULTIPLIER)
        val minRunLength = (width * MIN_ROW_RUN_FRACTION).toInt().coerceAtLeast(3)

        val rowEdges = ArrayList<RowEdge>(height)
        for (y in 0 until height) {
            val rowStart = y * width
            var left = -1
            var right = -1
            for (x in 0 until width) {
                val lum = luminance(pixels[rowStart + x])
                if (kotlin.math.abs(lum - background) >= threshold) {
                    if (left == -1) left = x
                    right = x
                }
            }
            if (left >= 0 && right >= 0 && (right - left + 1) >= minRunLength) {
                rowEdges += RowEdge(y = y, left = left, right = right)
            }
        }

        if (rowEdges.size < (height * MIN_FOREGROUND_ROWS_FRACTION).toInt().coerceAtLeast(6)) {
            return null
        }

        val bandSize = (rowEdges.size * BAND_FRACTION).toInt().coerceAtLeast(3)
        val topBand = rowEdges.take(bandSize)
        val bottomBand = rowEdges.takeLast(bandSize)

        val topLeft = Point2f(
            x = topBand.map { it.left }.average().toFloat(),
            y = topBand.map { it.y }.average().toFloat(),
        )
        val topRight = Point2f(
            x = topBand.map { it.right }.average().toFloat(),
            y = topBand.map { it.y }.average().toFloat(),
        )
        val bottomLeft = Point2f(
            x = bottomBand.map { it.left }.average().toFloat(),
            y = bottomBand.map { it.y }.average().toFloat(),
        )
        val bottomRight = Point2f(
            x = bottomBand.map { it.right }.average().toFloat(),
            y = bottomBand.map { it.y }.average().toFloat(),
        )

        val quad = Quad(
            topLeft = clampPoint(topLeft, width, height),
            topRight = clampPoint(topRight, width, height),
            bottomRight = clampPoint(bottomRight, width, height),
            bottomLeft = clampPoint(bottomLeft, width, height),
        )

        return quad.takeIf { it.isValid(MIN_QUAD_AREA_FRACTION, width, height) }
    }

    private fun sampleBorderLuminance(pixels: IntArray, width: Int, height: Int): List<Float> {
        val samples = ArrayList<Float>(width * 2 + height * 2)
        val stepX = (width / BORDER_SAMPLE_TARGET).coerceAtLeast(1)
        val stepY = (height / BORDER_SAMPLE_TARGET).coerceAtLeast(1)

        for (x in 0 until width step stepX) {
            samples += luminance(pixels[x]) // top
            samples += luminance(pixels[(height - 1) * width + x]) // bottom
        }
        for (y in 0 until height step stepY) {
            samples += luminance(pixels[y * width]) // left
            samples += luminance(pixels[y * width + (width - 1)]) // right
        }
        return samples
    }

    private fun luminance(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299f * r) + (0.587f * g) + (0.114f * b)
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2f
        } else {
            sorted[mid]
        }
    }

    private fun standardDeviation(values: List<Float>, mean: Float): Float {
        if (values.isEmpty()) return 0f
        var sum = 0f
        values.forEach { value ->
            val delta = value - mean
            sum += delta * delta
        }
        return kotlin.math.sqrt(sum / values.size)
    }

    private fun clampPoint(point: Point2f, width: Int, height: Int): Point2f {
        return Point2f(
            x = point.x.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat()),
            y = point.y.coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat()),
        )
    }

    private fun detectQuadNative(bitmap: Bitmap): Quad? {
        return null
    }

    companion object {
        private const val MAX_ANALYSIS_EDGE = 1200
        private const val MIN_DIMENSION = 24
        private const val MIN_QUAD_AREA_FRACTION = 0.05f
        private const val MIN_FOREGROUND_ROWS_FRACTION = 0.05f
        private const val MIN_ROW_RUN_FRACTION = 0.10f
        private const val BAND_FRACTION = 0.18f
        private const val BORDER_SAMPLE_TARGET = 64
        private const val MIN_LUMA_THRESHOLD = 16f
        private const val ADAPTIVE_STDDEV_MULTIPLIER = 1.8f

        @Volatile
        private var isOpenCvLoaded = false

        /**
         * Must be called from [PaperkeepApplication.onCreate] after
         * `OpenCVLoader.initDebug()` / `initAsync()` succeeds.
         */
        fun markLoaded() {
            isOpenCvLoaded = true
        }
    }

    private data class RowEdge(
        val y: Int,
        val left: Int,
        val right: Int,
    )
}
