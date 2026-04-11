package com.scanvault.feature.scanner.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Owns the camera permission state machine.
 *
 * The composable layer reads [state] and delegates all decisions here —
 * keeping the UI free of permission logic.
 */
@HiltViewModel
class CameraPermissionViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow<CameraPermissionState>(CameraPermissionState.NotRequested)
    val state: StateFlow<CameraPermissionState> = _state.asStateFlow()

    /** Called on initial composition to synchronise with the current system grant. */
    fun checkInitialPermission(context: Context) {
        if (isGranted(context)) {
            _state.value = CameraPermissionState.Granted
        }
    }

    /** Called after the system permission dialog returns a result. */
    fun onPermissionResult(granted: Boolean, canShowRationale: Boolean) {
        _state.value = when {
            granted -> CameraPermissionState.Granted
            canShowRationale -> CameraPermissionState.ShowRationale
            else -> CameraPermissionState.PermanentlyDenied
        }
    }

    /** Transition to rationale state so the rationale screen is shown before re-requesting. */
    fun showRationale() {
        if (_state.value == CameraPermissionState.NotRequested) {
            _state.value = CameraPermissionState.ShowRationale
        }
    }

    companion object {
        fun isGranted(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
    }
}
