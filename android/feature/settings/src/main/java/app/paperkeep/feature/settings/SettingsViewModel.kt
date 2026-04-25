package app.paperkeep.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.security.BiometricLockManager
import app.paperkeep.core.security.LockController
import app.paperkeep.core.security.LockTimeout
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val biometricLockEnabled: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val screenshotProtectionEnabled: Boolean = true,
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATE,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val biometricLockManager: BiometricLockManager,
    private val lockController: LockController,
) : ViewModel() {

    private val _screenshotProtection = MutableStateFlow(true)

    val uiState: StateFlow<SettingsUiState> = combine(
        biometricLockManager.isLockEnabled,
        lockController.lockTimeout,
        _screenshotProtection,
    ) { lockEnabled, timeout, screenshotProtection ->
        SettingsUiState(
            biometricLockEnabled = lockEnabled,
            isBiometricAvailable = biometricLockManager.isBiometricAvailable(),
            screenshotProtectionEnabled = screenshotProtection,
            lockTimeout = timeout,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(isBiometricAvailable = biometricLockManager.isBiometricAvailable()),
    )

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            biometricLockManager.setLockEnabled(enabled)
            if (!enabled) lockController.lockNow()
        }
    }

    fun setLockTimeout(timeout: LockTimeout) {
        viewModelScope.launch {
            lockController.setLockTimeout(timeout)
        }
    }

    fun setScreenshotProtection(enabled: Boolean) {
        _screenshotProtection.value = enabled
    }
}
