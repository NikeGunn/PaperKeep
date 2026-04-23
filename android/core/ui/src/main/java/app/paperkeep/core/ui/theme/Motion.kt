package app.paperkeep.core.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Paperkeep motion tokens — spring physics only, no linear curves.
 *
 * Per design spec §7:
 *   Standard: stiffness 380, damping 0.85 — every interactive state change
 *   EdgeSnap:  stiffness 600, damping 0.85 — edge-detection corner snap (tighter, snappier)
 */
object PaperkeepMotion {

    /**
     * Standard spring — used for all interactive UI state changes:
     * button presses, sheet reveals, card expansions, FAB transitions.
     */
    val Standard: SpringSpec<Float> = spring(
        stiffness = 380f,
        dampingRatio = 0.85f,
    )

    /**
     * Edge-snap spring — tighter, used for the edge-detection corner overlay
     * snapping to detected document corners. Feels physically crisp.
     */
    val EdgeSnap: SpringSpec<Float> = spring(
        stiffness = 600f,
        dampingRatio = 0.85f,
    )

    /**
     * Offset variant of Standard for Compose `animateFloatAsState` / `animateOffsetAsState`.
     * Same physics as [Standard] but for 2D coordinates.
     */
    val StandardDp: SpringSpec<Float> = spring(
        stiffness = 380f,
        dampingRatio = Spring.DampingRatioNoBouncy,   // 1.0 — no overshoot on Dp
    )
}
