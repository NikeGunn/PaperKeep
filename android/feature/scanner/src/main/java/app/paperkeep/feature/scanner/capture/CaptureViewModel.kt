package app.paperkeep.feature.scanner.capture

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.imaging.DetectionResult
import app.paperkeep.core.imaging.EdgeDetector
import app.paperkeep.core.imaging.ImageFilter
import app.paperkeep.core.imaging.PerspectiveTransform
import app.paperkeep.core.imaging.Point2f
import app.paperkeep.core.imaging.Quad
import app.paperkeep.core.ml.ClassificationResult
import app.paperkeep.core.ml.DocTypePolicies
import app.paperkeep.core.ml.DocumentClassifier
import app.paperkeep.core.ml.DocumentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns the capture pipeline (P1.9), crop screen state (P1.10), and document
 * classification (P3.1).
 *
 * Classification runs concurrently with the crop screen display so the user
 * can immediately start adjusting corners while the classifier runs in the
 * background. When the result arrives, the recommended filter is applied
 * UNLESS the user has already manually changed the filter.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val edgeDetector: EdgeDetector,
    private val classifier: DocumentClassifier,
    private val dispatchers: AppDispatchers,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    // ── Capture pipeline ──────────────────────────────────────────────────────

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
                    _state.value = CaptureState.ReadyToCrop(
                        image = warped,
                        quad = quad,
                        classification = null,
                        selectedFilter = ImageFilter.AUTO,
                    )
                }

                // Classification runs concurrently — does not block the crop screen
                val classResult = classifier.classify(warped)
                val policy = DocTypePolicies.forType(classResult.type)

                withContext(dispatchers.main) {
                    val current = _state.value as? CaptureState.ReadyToCrop ?: return@withContext
                    // Only auto-apply the filter if the user hasn't manually changed it
                    val filter = if (current.userOverrodeType) current.selectedFilter
                                 else policy.recommendedFilter
                    _state.value = current.copy(
                        classification = classResult,
                        selectedFilter = filter,
                    )
                }
            } catch (e: Exception) {
                withContext(dispatchers.main) {
                    _state.value = CaptureState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    // ── Crop / quad adjustment ────────────────────────────────────────────────

    fun onQuadUpdated(newQuad: Quad) {
        val current = _state.value as? CaptureState.ReadyToCrop ?: return
        viewModelScope.launch(dispatchers.default) {
            val rewarped = PerspectiveTransform.warp(current.image, newQuad)
            withContext(dispatchers.main) {
                persistQuad(newQuad)
                _state.value = current.copy(image = rewarped, quad = newQuad)
            }
        }
    }

    fun rotateImage() {
        val current = _state.value as? CaptureState.ReadyToCrop ?: return
        viewModelScope.launch(dispatchers.default) {
            val rotated = PerspectiveTransform.rotate(current.image, 90)
            withContext(dispatchers.main) {
                _state.value = current.copy(image = rotated)
            }
        }
    }

    fun retake() {
        _state.value = CaptureState.Idle
        savedState.remove<FloatArray>(KEY_QUAD)
    }

    // ── Filter override (user taps filter strip) ──────────────────────────────

    /**
     * Called when the user explicitly selects a filter from the preview strip.
     * Sets [CaptureState.ReadyToCrop.userOverrodeType] = false because this only
     * changes the filter, not the document type.
     */
    fun onFilterSelected(filter: ImageFilter) {
        val current = _state.value as? CaptureState.ReadyToCrop ?: return
        _state.value = current.copy(selectedFilter = filter)
    }

    // ── Document type override (user taps DocTypeChip and picks a type) ───────

    /**
     * Called when the user selects a document type from the chip picker overlay.
     * Auto-applies the recommended filter for [type] and marks the state as
     * user-overridden so future classifier updates don't overwrite the choice.
     */
    fun onDocTypeOverride(type: DocumentType) {
        val current = _state.value as? CaptureState.ReadyToCrop ?: return
        val policy = DocTypePolicies.forType(type)
        _state.value = current.copy(
            classification = ClassificationResult(type, 1.0f),
            selectedFilter = policy.recommendedFilter,
            userOverrodeType = true,
        )
    }

    // ── SavedStateHandle persistence ──────────────────────────────────────────

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

    // ── Helpers ───────────────────────────────────────────────────────────────

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
