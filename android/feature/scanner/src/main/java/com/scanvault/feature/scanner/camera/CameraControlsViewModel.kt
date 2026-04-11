package com.scanvault.feature.scanner.camera

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Owns the mutable state for all camera UI controls:
 *   - Torch toggle
 *   - Grid overlay
 *   - Zoom (pinch-to-zoom sets ratio, clamped to device min/max)
 *   - Tap-to-focus (stores the latest tap point, cleared after AF settles)
 */
@HiltViewModel
class CameraControlsViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(CameraControlsState())
    val state: StateFlow<CameraControlsState> = _state.asStateFlow()

    fun toggleTorch() {
        _state.update { it.copy(isTorchOn = !it.isTorchOn) }
    }

    fun toggleGrid() {
        _state.update { it.copy(isGridVisible = !it.isGridVisible) }
    }

    /**
     * Updates the zoom level, clamped to [CameraControlsState.minZoom] .. [CameraControlsState.maxZoom].
     * Called from the pinch gesture handler in the composable.
     */
    fun onZoomChanged(newRatio: Float) {
        _state.update { state ->
            state.copy(zoomRatio = newRatio.coerceIn(state.minZoom, state.maxZoom))
        }
    }

    /**
     * Records a tap-to-focus point. The composable shows the AF ring animation
     * at this coordinate, then calls [clearFocusPoint] once the animation ends.
     */
    fun onTapToFocus(x: Float, y: Float) {
        _state.update { it.copy(focusPoint = FocusPoint(x, y)) }
    }

    /** Called after the AF ring animation completes so it doesn't persist. */
    fun clearFocusPoint() {
        _state.update { it.copy(focusPoint = null) }
    }

    /** Sets device-reported min/max zoom on camera bind. */
    fun setZoomBounds(min: Float, max: Float) {
        _state.update { it.copy(minZoom = min, maxZoom = max) }
    }
}
