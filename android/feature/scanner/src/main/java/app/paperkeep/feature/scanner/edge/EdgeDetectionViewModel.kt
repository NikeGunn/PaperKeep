package app.paperkeep.feature.scanner.edge

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.imaging.DetectionResult
import app.paperkeep.core.imaging.EdgeDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Manages real-time edge detection results for the camera screen overlay.
 *
 * [processFrame] is called from the CameraX image analysis callback on a
 * background thread. Results are posted to [overlayState] on the main thread
 * for the Compose overlay to observe.
 *
 * Target: ≤16ms per frame on Snapdragon 6xx — achieved by:
 *   1. Running on [AppDispatchers.default] (thread pool)
 *   2. Processing a downsampled preview frame (640px wide)
 *   3. The OpenCV pipeline itself runs entirely in native code
 */
@HiltViewModel
class EdgeDetectionViewModel @Inject constructor(
    private val edgeDetector: EdgeDetector,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _overlayState = MutableStateFlow<EdgeOverlayState>(EdgeOverlayState.None)
    val overlayState: StateFlow<EdgeOverlayState> = _overlayState.asStateFlow()

    /**
     * Analyse a preview frame off the main thread and update [overlayState].
     * Safe to call from any thread; always suspends to [AppDispatchers.default].
     */
    fun processFrame(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) {
            _overlayState.value = EdgeOverlayState.None
            return
        }

        viewModelScope.launch(dispatchers.default) {
            val result = edgeDetector.detect(bitmap)
            val state = when (result) {
                is DetectionResult.Found -> EdgeOverlayState.Good(result.corners)
                DetectionResult.NotFound -> EdgeOverlayState.None
            }
            withContext(dispatchers.main) {
                _overlayState.value = state
            }
        }
    }

    /** Force the overlay to "partial" state — used by the composable when contours exist
     *  but no clean quad was extracted. */
    fun markPartial() {
        _overlayState.value = EdgeOverlayState.Partial
    }
}
