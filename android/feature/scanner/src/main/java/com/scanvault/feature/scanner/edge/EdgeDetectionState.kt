package com.scanvault.feature.scanner.edge

import com.scanvault.core.imaging.Quad

/**
 * The three visual states of the edge detection overlay (1B.12).
 */
sealed interface EdgeOverlayState {
    /** 4 valid corners detected — overlay drawn in green. */
    data class Good(val quad: Quad) : EdgeOverlayState

    /** Partial detection (contours found but no clean 4-corner quad) — overlay drawn in amber. */
    data object Partial : EdgeOverlayState

    /** Nothing detected — overlay is invisible. */
    data object None : EdgeOverlayState
}
