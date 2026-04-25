package app.paperkeep.feature.scanner.capture

import android.graphics.Bitmap
import app.paperkeep.core.imaging.ImageFilter
import app.paperkeep.core.imaging.Quad
import app.paperkeep.core.ml.ClassificationResult
import app.paperkeep.core.ml.DocumentType

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
     *
     * [image]              Warped (perspective-corrected) bitmap.
     * [quad]               Detected / user-adjusted crop corners.
     * [classification]     Result from [DocumentClassifier] — null while still running.
     * [selectedFilter]     Currently selected [ImageFilter] (auto-set from classification,
     *                      can be overridden by the user via the filter strip).
     * [userOverrodeType]   True when the user manually picked a different [DocumentType]
     *                      from the chip picker — prevents auto-overwrite on re-classify.
     */
    data class ReadyToCrop(
        val image: Bitmap,
        val quad: Quad,
        val classification: ClassificationResult? = null,
        val selectedFilter: ImageFilter = ImageFilter.AUTO,
        val userOverrodeType: Boolean = false,
    ) : CaptureState

    /** Something went wrong — [message] is for developer logging, not shown to users. */
    data class Error(val message: String) : CaptureState
}
