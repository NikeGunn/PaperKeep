package app.paperkeep.feature.scanner.edge

import app.paperkeep.core.imaging.Quad

/**
 * The visual states of the edge detection overlay.
 *
 * - [Good]    — confident 4-corner quad detected (confidence ≥ strong threshold) → green.
 * - [Weak]    — quad found but detector confidence is low → yellow/amber.
 * - [Settling]— quad detected and stabilising for auto-capture. [progress]
 *               ∈ [0,1] drives the ring fill animation; [confidence] picks the colour.
 * - [Locked]  — quad held still for ≥ 1.5s. Auto-capture should fire on this
 *               state if Magic Scan is enabled.
 * - [Partial] — contours but no clean quad — amber dashed border.
 * - [None]    — nothing detected; overlay invisible.
 */
sealed interface EdgeOverlayState {
    data class Good(val quad: Quad, val confidence: Float = 1f) : EdgeOverlayState
    data class Weak(val quad: Quad, val confidence: Float = 0f) : EdgeOverlayState
    data class Settling(val quad: Quad, val progress: Float, val confidence: Float = 1f) : EdgeOverlayState
    data class Locked(val quad: Quad) : EdgeOverlayState
    data object Partial : EdgeOverlayState
    data object None : EdgeOverlayState
}
