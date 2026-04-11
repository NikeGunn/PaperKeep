package com.scanvault.core.imaging

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
}

/**
 * Result of an edge detection pass.
 */
sealed interface DetectionResult {
    /**
     * A document quad was found.
     * Corners are in image-pixel coordinates, ordered: top-left, top-right,
     * bottom-right, bottom-left.
     */
    data class Found(val corners: Quad) : DetectionResult

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
