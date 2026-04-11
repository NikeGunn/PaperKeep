package com.scanvault.core.imaging

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for 1B.11: Edge detector contract.
 *
 * Uses [FakeEdgeDetector] which is deterministic and needs no native libs.
 * Also verifies the [Quad] geometry utilities.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FakeEdgeDetectorTest {

    private val detector = FakeEdgeDetector()

    // ── 1B.11: Find rectangle in synthetic image ───────────────────────────────

    @Test
    fun detect_onNonUniformBitmap_returnsFound() {
        val bitmap = createNonUniformBitmap(width = 640, height = 480)

        val result = detector.detect(bitmap)

        assertTrue(
            "Detector must return Found for a non-uniform image",
            result is DetectionResult.Found
        )
    }

    @Test
    fun detect_foundResult_hasValidQuad() {
        val bitmap = createNonUniformBitmap(width = 640, height = 480)

        val result = detector.detect(bitmap) as DetectionResult.Found

        assertNotNull(result.corners)
        assertTrue(result.corners.area() > 0f)
    }

    @Test
    fun detect_foundResult_quadCoversReasonableArea() {
        val w = 640
        val h = 480
        val bitmap = createNonUniformBitmap(width = w, height = h)

        val result = detector.detect(bitmap) as DetectionResult.Found
        val quad = result.corners

        assertTrue(
            "Detected quad must cover > 5% of image area",
            quad.isValid(minAreaFraction = 0.05f, imageWidth = w, imageHeight = h)
        )
    }

    // ── 1B.11: Empty/uniform image returns NotFound ────────────────────────────

    @Test
    fun detect_onUniformWhiteBitmap_returnsNotFound() {
        val bitmap = createUniformBitmap(width = 640, height = 480, color = Color.WHITE)

        val result = detector.detect(bitmap)

        assertTrue(
            "Detector must return NotFound for a uniform white image",
            result is DetectionResult.NotFound
        )
    }

    @Test
    fun detect_onNullEquivalent_emptyBitmap_returnsNotFound() {
        // 1×1 pixel uniform image acts as an edge case
        val bitmap = createUniformBitmap(width = 1, height = 1, color = Color.WHITE)

        val result = detector.detect(bitmap)

        assertTrue(result is DetectionResult.NotFound)
    }

    // ── 1B.11: Processing must not block main thread ───────────────────────────

    @Test
    fun detect_completes_withoutException() {
        // Verifies the detector doesn't throw for either uniform or non-uniform input
        val uniform = createUniformBitmap(640, 480, Color.WHITE)
        val nonUniform = createNonUniformBitmap(640, 480)

        // No exception thrown = passes
        detector.detect(uniform)
        detector.detect(nonUniform)
    }

    // ── 1B.11: Handles various bitmap sizes ───────────────────────────────────

    @Test
    fun detect_handlesLargeBitmap() {
        val bitmap = createNonUniformBitmap(width = 4000, height = 3000)
        val result = detector.detect(bitmap)
        assertTrue(result is DetectionResult.Found)
    }

    // ── Quad geometry ──────────────────────────────────────────────────────────

    @Test
    fun quad_area_isCorrectForKnownShape() {
        // A 100×100 square has area 10000
        val square = Quad(
            topLeft = Point2f(0f, 0f),
            topRight = Point2f(100f, 0f),
            bottomRight = Point2f(100f, 100f),
            bottomLeft = Point2f(0f, 100f),
        )
        assertEquals(10000f, square.area(), 0.01f)
    }

    @Test
    fun quad_isValid_trueForLargeQuad() {
        val quad = Quad(
            topLeft = Point2f(64f, 48f),
            topRight = Point2f(576f, 48f),
            bottomRight = Point2f(576f, 432f),
            bottomLeft = Point2f(64f, 432f),
        )
        assertTrue(quad.isValid(0.05f, imageWidth = 640, imageHeight = 480))
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun createUniformBitmap(width: Int, height: Int, color: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
            for (y in 0 until height) {
                for (x in 0 until width) {
                    bmp.setPixel(x, y, color)
                }
            }
        }
    }

    private fun createNonUniformBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
            // White background
            for (y in 0 until height) {
                for (x in 0 until width) {
                    bmp.setPixel(x, y, Color.WHITE)
                }
            }
            // Dark rectangle in the middle — simulates a document
            val margin = (width * 0.15).toInt()
            val marginV = (height * 0.15).toInt()
            for (y in marginV until (height - marginV)) {
                for (x in margin until (width - margin)) {
                    bmp.setPixel(x, y, Color.DKGRAY)
                }
            }
        }
    }
}
