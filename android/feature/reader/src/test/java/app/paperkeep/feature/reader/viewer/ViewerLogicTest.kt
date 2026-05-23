package app.paperkeep.feature.reader.viewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import app.paperkeep.core.domain.model.Page
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic unit tests for the mixed-orientation viewer (Feature 2).
 *
 * Both [clampOffsetToBounds] and [aspectRatioFor] are pure functions; no
 * Compose or Android dependencies needed.
 */
class ViewerLogicTest {

    private val slot = Size(1000f, 1500f) // portrait

    @Test
    fun clamp_atScaleOne_returnsZero() {
        assertEquals(Offset.Zero, clampOffsetToBounds(Offset(100f, 100f), slot, 1f))
        assertEquals(Offset.Zero, clampOffsetToBounds(Offset(100f, 100f), slot, 0.5f))
    }

    @Test
    fun clamp_atScaleTwo_limitsToHalfSlotPerAxis() {
        // (s-1)/2 * slotW = 500, slotH = 750
        val clamped = clampOffsetToBounds(Offset(9999f, 9999f), slot, 2f)
        assertEquals(500f, clamped.x, 0.0001f)
        assertEquals(750f, clamped.y, 0.0001f)
    }

    @Test
    fun clamp_negativeOffsetsAreSymmetric() {
        val clamped = clampOffsetToBounds(Offset(-9999f, -9999f), slot, 2f)
        assertEquals(-500f, clamped.x, 0.0001f)
        assertEquals(-750f, clamped.y, 0.0001f)
    }

    @Test
    fun clamp_smallOffsetUnchangedWithinBounds() {
        val in_ = Offset(120f, -50f)
        val clamped = clampOffsetToBounds(in_, slot, 3f)
        assertEquals(in_.x, clamped.x, 0.0001f)
        assertEquals(in_.y, clamped.y, 0.0001f)
    }

    @Test
    fun clamp_zeroSlotReturnsZero() {
        assertEquals(Offset.Zero, clampOffsetToBounds(Offset(100f, 100f), Size.Zero, 2f))
        assertEquals(Offset.Zero, clampOffsetToBounds(Offset(100f, 100f), Size(0f, 100f), 2f))
    }

    @Test
    fun aspectRatio_portraitPage() {
        val page = page(width = 800, height = 1200)
        assertEquals(800f / 1200f, aspectRatioFor(page), 0.0001f)
    }

    @Test
    fun aspectRatio_landscapePage() {
        val page = page(width = 1600, height = 900)
        assertEquals(1600f / 900f, aspectRatioFor(page), 0.0001f)
    }

    @Test
    fun aspectRatio_zeroDimsFallsBackToOne() {
        val page = page(width = 0, height = 100)
        assertEquals(1f, aspectRatioFor(page), 0.0001f)
    }

    @Test
    fun aspectRatio_squareIsOne() {
        val page = page(width = 500, height = 500)
        assertEquals(1f, aspectRatioFor(page), 0.0001f)
    }

    private fun page(width: Int, height: Int): Page = Page(
        id = "p",
        documentId = "d",
        pageIndex = 0,
        encryptedImagePath = "",
        encryptedThumbPath = "",
        ocrStatus = "pending",
        ocrLanguage = null,
        ocrText = null,
        width = width,
        height = height,
        filter = "original",
        title = null,
    )
}
