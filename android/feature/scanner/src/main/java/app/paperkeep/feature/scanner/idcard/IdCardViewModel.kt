package app.paperkeep.feature.scanner.idcard

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.feature.scanner.IdCardCaptureMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Orchestrates the two-step ID card flow (P3.2).
 *
 *  AwaitingFront → user scans front  → [onFrontConfirmed]
 *  AwaitingBack  → user scans back   → [onBackConfirmed]
 *  Done          → composite ready
 *
 * Call [reset] to start over from AwaitingFront.
 */
@HiltViewModel
class IdCardViewModel @Inject constructor(
    private val captureMode: IdCardCaptureMode,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _state = MutableStateFlow<IdCardFlowState>(IdCardFlowState.AwaitingFront)
    val state: StateFlow<IdCardFlowState> = _state.asStateFlow()

    /**
     * Called after the user confirms the crop on the front-side image.
     * Advances the state to [IdCardFlowState.AwaitingBack].
     */
    fun onFrontConfirmed(bitmap: Bitmap) {
        captureMode.captureFront(bitmap)
        _state.value = IdCardFlowState.AwaitingBack(frontPreview = bitmap)
    }

    /**
     * Called after the user confirms the crop on the back-side image.
     * Triggers composite building on the default dispatcher, then emits [IdCardFlowState.Done].
     */
    fun onBackConfirmed(bitmap: Bitmap) {
        captureMode.captureBack(bitmap)
        viewModelScope.launch(dispatchers.default) {
            val result = runCatching { captureMode.buildComposite() }
            withContext(dispatchers.main) {
                result.fold(
                    onSuccess  = { _state.value = IdCardFlowState.Done(it.page) },
                    onFailure  = { _state.value = IdCardFlowState.Error(it.message ?: "composite failed") },
                )
            }
        }
    }

    /** Retake the front side — goes back to [IdCardFlowState.AwaitingFront]. */
    fun retakeFront() {
        captureMode.reset()
        _state.value = IdCardFlowState.AwaitingFront
    }

    /** Retake the back side — stays at [IdCardFlowState.AwaitingBack] (keeps front). */
    fun retakeBack() {
        val current = _state.value as? IdCardFlowState.AwaitingBack ?: return
        captureMode.captureBack(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))  // clear back
        _state.value = current  // re-emit same AwaitingBack to trigger recomposition
    }

    /** Full reset — returns to [IdCardFlowState.AwaitingFront]. */
    fun reset() {
        captureMode.reset()
        _state.value = IdCardFlowState.AwaitingFront
    }
}
