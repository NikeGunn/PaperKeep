package app.paperkeep.feature.scanner.permission

/**
 * Sealed hierarchy representing all possible states of the camera permission
 * flow, driving the rationale screen, denial state, and settings redirect.
 */
sealed interface CameraPermissionState {
    /** Permission not yet requested or requestable again. */
    data object NotRequested : CameraPermissionState

    /** Permission was granted — camera screen can be shown. */
    data object Granted : CameraPermissionState

    /**
     * Permission was denied — user tapped "Deny" once.
     * We can still show the rationale and re-request.
     */
    data object ShowRationale : CameraPermissionState

    /**
     * Permission permanently denied ("Don't ask again" was checked).
     * The only option is directing the user to system Settings.
     */
    data object PermanentlyDenied : CameraPermissionState
}
