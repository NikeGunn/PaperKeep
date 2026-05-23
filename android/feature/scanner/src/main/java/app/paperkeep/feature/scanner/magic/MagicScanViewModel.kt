package app.paperkeep.feature.scanner.magic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MagicScanViewModel @Inject constructor(
    private val prefs: MagicScanPreferences,
) : ViewModel() {

    val isEnabled: StateFlow<Boolean> = prefs.isEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MagicScanPreferences.DEFAULT_ENABLED,
        )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setEnabled(enabled) }
    }
}
