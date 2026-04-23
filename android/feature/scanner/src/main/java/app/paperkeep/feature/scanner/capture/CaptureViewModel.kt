package app.paperkeep.feature.scanner.capture

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.imaging.DetectionResult
import app.paperkeep.core.imaging.EdgeDetector
import app.paperkeep.core.imaging.PerspectiveTransform
import app.paperkeep.core.imaging.Point2f
import app.paperkeep.core.imaging.Quad
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns the capture pipeline (1B.13) and the crop screen state (1B.14).
 *
 * All image processing runs on [AppDispatchers.default] — never the main thread.
 * [SavedStateHandle] is used so the crop quad survives process death / rotation
 * (state serialisation uses primitive Float values).
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val edgeDetector: EdgeDetector,
    private val dispatchers: AppDispatchers,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    // ── 1B.13: Capture pipeline ────────────────────────────────────────────────

    /**
     * Entry point called when the user taps the capture button.
     * [bitmap] is the full-resolution image from CameraX ImageCapture.
     */
    fun onImageCaptured(bitmap: Bitmap) {
        _state.value = CaptureState.Processing(bitmap)

        viewModelScope.launch(dispatchers.default) {
            try {
                val result = edgeDetector.detect(bitmap)
                val quad = when (result) {
                    is DetectionResult.Found -> result.corners
                    DetectionResult.NotFound -> fullImageQuad(bitmap)
                }

                val warped = PerspectiveTransform.warp(bitmap, quad)

                withContext(dispatchers.main) {
                    persistQuad(quad)
                    _state.value = CaptureState.ReadyToCrop(warped, quad)
                }
            } catch (e: Exception) {
                withContext(dispatchers.main) {
                    _state.value = CaptureState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    // ── 1B.14: Manual crop / quad adjustment ──────────────────────────────────

    /**
     * Called when the user drags a corner on the crop screen.
     * Updates the current quad and re-warps the preview at reduced resolution.
     */
    fun onQuadUpdated(newQuad: Quad) {
        val current = _state.value
        if (current !is CaptureState.ReadyToCrop) return

        viewModelScope.launch(dispatchers.default) {
            val rewarped = PerspectiveTransform.warp(current.image, newQuad)
            withContext(dispatchers.main) {
                persistQuad(newQuad)
                _state.value = current.copy(image = rewarped, quad = newQuad)
            }
        }
    }

    /** Rotate the current cropped image 90° clockwise. */
    fun rotateImage() {
        val current = _state.value
        if (current !is CaptureState.ReadyToCrop) return

        viewModelScope.launch(dispatchers.default) {
            val rotated = PerspectiveTransform.rotate(current.image, 90)
            withContext(dispatchers.main) {
                _state.value = current.copy(image = rotated)
            }
        }
    }

    /** Go back to the camera — discard the current capture. */
    fun retake() {
        _state.value = CaptureState.Idle
        savedState.remove<FloatArray>(KEY_QUAD)
    }

    // ── SavedStateHandle persistence (survives rotation) ──────────────────────

    private fun persistQuad(quad: Quad) {
        savedState[KEY_QUAD] = floatArrayOf(
            quad.topLeft.x, quad.topLeft.y,
            quad.topRight.x, quad.topRight.y,
            quad.bottomRight.x, quad.bottomRight.y,
            quad.bottomLeft.x, quad.bottomLeft.y,
        )
    }

    fun restoreQuadFromSavedState(): Quad? {
        val arr = savedState.get<FloatArray>(KEY_QUAD) ?: return null
        if (arr.size != 8) return null
        return Quad(
            topLeft = Point2f(arr[0], arr[1]),
            topRight = Point2f(arr[2], arr[3]),
            bottomRight = Point2f(arr[4], arr[5]),
            bottomLeft = Point2f(arr[6], arr[7]),
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun fullImageQuad(bitmap: Bitmap): Quad {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return Quad(
            topLeft = Point2f(0f, 0f),
            topRight = Point2f(w, 0f),
            bottomRight = Point2f(w, h),
            bottomLeft = Point2f(0f, h),
        )
    }

    companion object {
        private const val KEY_QUAD = "capture_quad"
    }
}
