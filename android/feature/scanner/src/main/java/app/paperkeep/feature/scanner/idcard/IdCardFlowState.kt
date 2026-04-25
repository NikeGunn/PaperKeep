package app.paperkeep.feature.scanner.idcard

import android.graphics.Bitmap

/** State machine for the two-step ID card capture flow (P3.2). */
sealed interface IdCardFlowState {

    /** No capture started yet — waiting for the user to scan the front. */
    data object AwaitingFront : IdCardFlowState

    /**
     * Front side captured and confirmed.
     * [frontPreview] is displayed as a thumbnail while the user scans the back.
     */
    data class AwaitingBack(val frontPreview: Bitmap) : IdCardFlowState

    /**
     * Both sides captured — composite is ready.
     * [composite] is the single A4 page bitmap ready to be saved.
     */
    data class Done(val composite: Bitmap) : IdCardFlowState

    /** Something went wrong; [message] is for developer logging only. */
    data class Error(val message: String) : IdCardFlowState
}
