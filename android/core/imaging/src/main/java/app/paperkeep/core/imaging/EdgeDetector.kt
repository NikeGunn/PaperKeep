package app.paperkeep.core.imaging

import android.graphics.Bitmap

/**
 * Detects document edges in a bitmap and returns the four corners of the best
 * quadrilateral, or null if no document is found.
 *
 * The interface is the testable contract; [OpenCvEdgeDetector] provides the
 * real OpenCV implementation. Tests inject [FakeEdgeDetector].
 */
interface EdgeDetector {
    /**
     * Analyse [bitmap] and return a [DetectionResult].
     * Must be called from a background thread (never the main thread).
     *
     * @param bitmap Input image — may be the live preview frame (downsampled)
     *               or the full-resolution captured image.
     * @return [DetectionResult.Found] with the 4 corners if a quadrilateral is
     *         detected, [DetectionResult.NotFound] otherwise.
     */
    fun detect(bitmap: Bitmap): DetectionResult

    /**
     * Tap-anchored detection: find the document quad that contains the tap
     * point [tapX], [tapY] (in [bitmap] pixel coordinates).
     *
     * Used by the "tap to detect" UX: instead of the detector guessing the
     * largest rectangle in the whole frame (which may be a wall edge, a
     * neighbouring page, or a piece of furniture), the user points at the
     * document they actually want to scan and the detector locks onto it.
     *
     * Default implementation falls back to [detect] for fakes/JVM paths that
     * don't implement tap-anchoring.
     */
    fun detectAt(bitmap: Bitmap, tapX: Float, tapY: Float): DetectionResult = detect(bitmap)
}

/**
 * Result of an edge detection pass.
 */
sealed interface DetectionResult {
    /**
     * A document quad was found.
     * Corners are in image-pixel coordinates, ordered: top-left, top-right,
     * bottom-right, bottom-left.
     *
     * [confidence] ∈ [0,1] — the detector's normalised quality score for this
     * quad. UI uses this to choose overlay colour: strong (green) for confident
     * detections, weak (yellow) for tentative ones below the strong threshold.
     */
    data class Found(val corners: Quad, val confidence: Float = 1f) : DetectionResult

    /**
     * No document quad detected in the image.
     */
    data object NotFound : DetectionResult
}

/**
 * A document quadrilateral defined by its four corners.
 * Coordinates are in the coordinate space of the input bitmap.
 */
data class Quad(
    val topLeft: Point2f,
    val topRight: Point2f,
    val bottomRight: Point2f,
    val bottomLeft: Point2f,
) {
    /** True when the quad has a reasonable area (> threshold). */
    fun isValid(minAreaFraction: Float = 0.05f, imageWidth: Int, imageHeight: Int): Boolean {
        val imageArea = imageWidth.toFloat() * imageHeight.toFloat()
        return area() >= imageArea * minAreaFraction
    }

    /** Shoelace formula for the area of the quad. */
    fun area(): Float {
        val pts = listOf(topLeft, topRight, bottomRight, bottomLeft)
        var area = 0f
        val n = pts.size
        for (i in pts.indices) {
            val j = (i + 1) % n
            area += pts[i].x * pts[j].y
            area -= pts[j].x * pts[i].y
        }
        return Math.abs(area) / 2f
    }
}

data class Point2f(val x: Float, val y: Float)
