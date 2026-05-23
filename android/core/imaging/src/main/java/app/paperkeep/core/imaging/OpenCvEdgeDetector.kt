package app.paperkeep.core.imaging

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production document-edge detector.
 *
 * On real devices ([isOpenCvLoaded] = true) this delegates to [OpenCvBridge]
 * which runs the full multi-stage pipeline (bilateral + CLAHE + Otsu-Canny +
 * adaptive threshold + multi-criterion contour scoring + cornerSubPix).
 *
 * On JVM unit tests / fallback paths it uses [ContourQuadFinder] — a
 * pure-Kotlin re-implementation with no native dependencies.
 *
 * Confidence is forwarded through [DetectionResult.Found.confidence] so the
 * UI can render different overlay colours for strong vs weak detections.
 */
@Singleton
class OpenCvEdgeDetector @Inject constructor() : EdgeDetector {

    override fun detect(bitmap: Bitmap): DetectionResult = detectInternal(bitmap, null, null)

    override fun detectAt(bitmap: Bitmap, tapX: Float, tapY: Float): DetectionResult =
        detectInternal(bitmap, tapX, tapY)

    private fun detectInternal(bitmap: Bitmap, tapX: Float?, tapY: Float?): DetectionResult {
        if (bitmap.isRecycled || bitmap.width < 2 || bitmap.height < 2) {
            return DetectionResult.NotFound
        }

        if (isOpenCvLoaded) {
            val native = OpenCvBridge.detectQuad(bitmap, tapX, tapY)
            // When the user has tapped, accept smaller quads (they explicitly
            // pointed at the document — don't second-guess the user's intent
            // with a 5% area floor).
            val floor = if (tapX != null) MIN_QUAD_AREA_FRACTION_TAPPED else MIN_QUAD_AREA_FRACTION
            if (native != null && native.quad.isValid(floor, bitmap.width, bitmap.height)) {
                return DetectionResult.Found(native.quad, native.confidence)
            }
            // Do NOT silently fall through to full-frame — return NotFound so the UI
            // can show "searching" state rather than a confidently wrong quad.
            return DetectionResult.NotFound
        }

        // JVM / fallback path
        val analysisBitmap = downsampleForAnalysis(bitmap)
        val luma = bitmapToLuma(analysisBitmap)
        val scored = ContourQuadFinder.findScored(luma, analysisBitmap.width, analysisBitmap.height)
            ?: return DetectionResult.NotFound

        val scaleX = bitmap.width.toFloat() / analysisBitmap.width.toFloat()
        val scaleY = bitmap.height.toFloat() / analysisBitmap.height.toFloat()
        val mapped = Quad(
            topLeft = Point2f(scored.quad.topLeft.x * scaleX, scored.quad.topLeft.y * scaleY),
            topRight = Point2f(scored.quad.topRight.x * scaleX, scored.quad.topRight.y * scaleY),
            bottomRight = Point2f(scored.quad.bottomRight.x * scaleX, scored.quad.bottomRight.y * scaleY),
            bottomLeft = Point2f(scored.quad.bottomLeft.x * scaleX, scored.quad.bottomLeft.y * scaleY),
        )
        return if (mapped.isValid(MIN_QUAD_AREA_FRACTION, bitmap.width, bitmap.height)) {
            DetectionResult.Found(mapped, scored.score)
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

    private fun bitmapToLuma(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val luma = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
        }
        return luma
    }

    companion object {
        private const val MAX_ANALYSIS_EDGE = 640
        private const val MIN_QUAD_AREA_FRACTION = 0.05f
        private const val MIN_QUAD_AREA_FRACTION_TAPPED = 0.015f

        @Volatile
        private var isOpenCvLoaded = false

        fun markLoaded() { isOpenCvLoaded = true }

        @get:JvmStatic
        internal val isLoaded: Boolean get() = isOpenCvLoaded
    }
}
