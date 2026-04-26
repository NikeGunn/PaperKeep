package app.paperkeep.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.feature.onboarding.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Exposes onboarding completion for nav start-destination decisions.
 *
 * Null means loading: DataStore has not emitted yet.
 */
@HiltViewModel
class OnboardingNavViewModel @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
) : ViewModel() {

    private val _onboardingCompleted = MutableStateFlow<Boolean?>(null)
    val onboardingCompleted: StateFlow<Boolean?> = _onboardingCompleted.asStateFlow()

    init {
        viewModelScope.launch {
            onboardingRepository.isCompleted.collect { completed ->
                _onboardingCompleted.value = completed
            }
        }
    }
}
