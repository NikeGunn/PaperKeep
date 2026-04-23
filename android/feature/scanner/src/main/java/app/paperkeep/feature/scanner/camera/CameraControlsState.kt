package app.paperkeep.feature.scanner.camera

/**
 * Immutable UI state for the camera controls overlay.
 * Hoisted to [CameraControlsViewModel] so controls survive recomposition.
 */
data class CameraControlsState(
    val isTorchOn: Boolean = false,
    val isGridVisible: Boolean = false,
    val zoomRatio: Float = 1.0f,
    val minZoom: Float = 0.6f,
    val maxZoom: Float = 3.0f,
    /** Non-null when tap-to-focus is active and the AF ring should be shown. */
    val focusPoint: FocusPoint? = null,
)

data class FocusPoint(val x: Float, val y: Float)
