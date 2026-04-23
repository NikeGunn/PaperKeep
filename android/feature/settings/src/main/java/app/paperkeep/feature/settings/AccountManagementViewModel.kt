package app.paperkeep.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// TODO P1.8: Account management removed — Paperkeep v2 has no account/backend.
// This ViewModel is a no-op stub kept so the navigation graph and SettingsScreen
// can still reference the type without compile errors. Wire backup/restore here in Phase 4.

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
class AccountManagementViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AccountManagementUiState())
    val uiState: StateFlow<AccountManagementUiState> = _uiState.asStateFlow()

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearEvent() {
        _uiState.value = _uiState.value.copy(event = null)
    }
}
