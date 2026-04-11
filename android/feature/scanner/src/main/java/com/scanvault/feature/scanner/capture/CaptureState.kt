package com.scanvault.feature.scanner.capture

import android.graphics.Bitmap
import com.scanvault.core.imaging.Quad

/** All possible states of the capture + crop pipeline. */
sealed interface CaptureState {
    /** Camera is live — no capture in progress. */
    data object Idle : CaptureState

    /** Capture triggered, waiting for full-res image from CameraX. */
    data object Capturing : CaptureState

    /** Image captured, edge detection + perspective warp running. */
    data class Processing(val raw: Bitmap) : CaptureState

    /**
     * Processing complete — crop screen is shown.
     * [image] is the warped result (may still need manual crop adjustment).
     * [quad] is the detected / user-adjusted quad in original-image coordinates.
     */
    data class ReadyToCrop(
        val image: Bitmap,
        val quad: Quad,
    ) : CaptureState

    /** Something went wrong — [message] is for developer logging, not shown to users. */
    data class Error(val message: String) : CaptureState
}
