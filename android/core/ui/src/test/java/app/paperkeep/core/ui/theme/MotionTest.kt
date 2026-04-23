package app.paperkeep.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Verifies the Paperkeep motion token spec (§7):
 *   Standard  — stiffness 380, damping 0.85
 *   EdgeSnap  — stiffness 600, damping 0.85
 */
class MotionTest {

    @Test
    fun standard_stiffness_is380() {
        assertEquals(
            "Standard spring stiffness must be 380 (spec §7)",
            380f,
            PaperkeepMotion.Standard.stiffness,
            0.001f,
        )
    }

    @Test
    fun standard_dampingRatio_is085() {
        assertEquals(
            "Standard spring damping must be 0.85 (spec §7)",
            0.85f,
            PaperkeepMotion.Standard.dampingRatio,
            0.001f,
        )
    }

    @Test
    fun edgeSnap_stiffness_is600() {
        assertEquals(
            "EdgeSnap spring stiffness must be 600 (spec §7 — tighter corner snap)",
            600f,
            PaperkeepMotion.EdgeSnap.stiffness,
            0.001f,
        )
    }

    @Test
    fun edgeSnap_dampingRatio_is085() {
        assertEquals(
            "EdgeSnap spring damping must be 0.85 (spec §7)",
            0.85f,
            PaperkeepMotion.EdgeSnap.dampingRatio,
            0.001f,
        )
    }

    @Test
    fun edgeSnap_isStifferThanStandard() {
        // EdgeSnap must feel tighter than Standard — higher stiffness = faster response
        assert(PaperkeepMotion.EdgeSnap.stiffness > PaperkeepMotion.Standard.stiffness) {
            "EdgeSnap stiffness (${PaperkeepMotion.EdgeSnap.stiffness}) must exceed " +
            "Standard stiffness (${PaperkeepMotion.Standard.stiffness})"
        }
    }

    @Test
    fun standard_and_edgeSnap_shareDampingRatio() {
        // Both use 0.85 — spec intends the same feel, just different speed
        assertEquals(
            PaperkeepMotion.Standard.dampingRatio,
            PaperkeepMotion.EdgeSnap.dampingRatio,
            0.001f,
        )
    }

    @Test
    fun standardDp_hasNoBounce() {
        // StandardDp uses DampingRatioNoBouncy (1.0) for use with Dp values
        // where overshoot would look wrong
        assertEquals(
            "StandardDp must use no-bouncy ratio for layout animations",
            1.0f,
            PaperkeepMotion.StandardDp.dampingRatio,
            0.001f,
        )
    }

    @Test
    fun tokenNames_areDistinct() {
        // Sanity: the three specs are different objects with different physics
        assertNotEquals(PaperkeepMotion.Standard.stiffness, PaperkeepMotion.EdgeSnap.stiffness)
        assertNotEquals(PaperkeepMotion.Standard.dampingRatio, PaperkeepMotion.StandardDp.dampingRatio)
    }
}
