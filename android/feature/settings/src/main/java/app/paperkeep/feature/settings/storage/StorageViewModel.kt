package app.paperkeep.feature.settings.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.backup.storage.StorageReport
import app.paperkeep.core.backup.storage.StorageReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StorageUiState(
    val report: StorageReport = StorageReport(0, 0, 0, 0, 0, 0),
    val message: String? = null,
    val refreshing: Boolean = false,
)

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val reporter: StorageReporter,
) : ViewModel() {

    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            val report = withContext(Dispatchers.IO) { reporter.report() }
            _state.value = StorageUiState(report = report, refreshing = false)
        }
    }

    fun clearTransientCaches() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) { reporter.clearTransientCaches() }
            _state.value = _state.value.copy(message = "Cleared ${freed / 1024} KiB of share/export cache")
            refresh()
        }
    }

    fun clearCrashLogs() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) { reporter.clearCrashLogs() }
            _state.value = _state.value.copy(message = "Cleared ${freed / 1024} KiB of crash logs")
            refresh()
        }
    }

    fun acknowledge() {
        _state.value = _state.value.copy(message = null)
    }
}
