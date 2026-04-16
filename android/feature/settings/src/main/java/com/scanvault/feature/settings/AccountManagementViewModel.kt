package com.scanvault.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanvault.core.crypto.KeyDerivation
import com.scanvault.core.crypto.KeyRotation
import com.scanvault.core.network.api.ScanVaultApiClient
import com.scanvault.core.network.auth.TokenStore
import com.scanvault.core.network.model.ChangePasswordRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject

data class AccountManagementUiState(
    val isLoading: Boolean = false,
    val event: AccountManagementEvent? = null,
    val error: String? = null,
)

enum class AccountManagementEvent {
    PASSWORD_CHANGED,
    ACCOUNT_DELETED,
    LOGGED_OUT,
}

@HiltViewModel
class AccountManagementViewModel @Inject constructor(
    private val apiClient: ScanVaultApiClient,
    private val tokenStore: TokenStore,
    private val keyDerivation: KeyDerivation,
    private val keyRotation: KeyRotation,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountManagementUiState())
    val uiState: StateFlow<AccountManagementUiState> = _uiState.asStateFlow()

    /**
     * Change password:
     * 1. Derive old and new master keys (PBKDF2)
     * 2. Re-wrap the vault key under the new password-derived key
     * 3. POST /v1/accounts/me/password with updated wrapped key
     */
    fun changePassword(
        currentPassword: String,
        newPassword: String,
        currentWrappedKey: ByteArray,
        kdfSalt: ByteArray,
    ) {
        viewModelScope.launch {
            _uiState.value = AccountManagementUiState(isLoading = true)
            try {
                val oldKey = keyDerivation.deriveKey(currentPassword.toCharArray(), kdfSalt)
                val newSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val newKey = keyDerivation.deriveKey(newPassword.toCharArray(), newSalt)

                // Re-wrap vault key: decrypt with old key, encrypt with new key
                val newWrappedKey = keyRotation.rewrapKey(
                    oldKey = oldKey,
                    newKey = newKey,
                    wrappedKey = currentWrappedKey,
                )

                val b64 = Base64.getEncoder()
                val request = ChangePasswordRequest(
                    currentAuthHash = b64.encodeToString(oldKey),
                    newAuthHash = b64.encodeToString(newKey),
                    newAuthParams = mapOf(
                        "algorithm" to "pbkdf2-sha256",
                        "iterations" to "${KeyDerivation.DEFAULT_ITERATIONS}",
                    ),
                    wrappedKey = b64.encodeToString(newWrappedKey),
                    kdfSalt = b64.encodeToString(newSalt),
                    kdfParams = mapOf(
                        "algorithm" to "pbkdf2-sha256",
                        "iterations" to "${KeyDerivation.DEFAULT_ITERATIONS}",
                    ),
                )

                apiClient.changePassword(request).fold(
                    onSuccess = {
                        _uiState.value = AccountManagementUiState(event = AccountManagementEvent.PASSWORD_CHANGED)
                    },
                    onFailure = { e ->
                        _uiState.value = AccountManagementUiState(error = e.message ?: "Password change failed")
                    },
                )
            } catch (e: Exception) {
                _uiState.value = AccountManagementUiState(error = e.message ?: "Password change failed")
            }
        }
    }

    /** DELETE /v1/accounts/me — clear remote data then local tokens. */
    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = AccountManagementUiState(isLoading = true)
            apiClient.deleteAccount().fold(
                onSuccess = {
                    _uiState.value = AccountManagementUiState(event = AccountManagementEvent.ACCOUNT_DELETED)
                },
                onFailure = { e ->
                    _uiState.value = AccountManagementUiState(error = e.message ?: "Delete account failed")
                },
            )
        }
    }

    /**
     * Logout: wipes tokens from EncryptedSharedPreferences.
     * Local documents are preserved — user can log back in.
     */
    fun logout() {
        tokenStore.clearAll()
        _uiState.value = AccountManagementUiState(event = AccountManagementEvent.LOGGED_OUT)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearEvent() {
        _uiState.value = _uiState.value.copy(event = null)
    }
}
