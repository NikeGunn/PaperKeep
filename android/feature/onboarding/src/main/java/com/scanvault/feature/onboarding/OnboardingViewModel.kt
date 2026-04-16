package com.scanvault.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Total number of onboarding pages. */
const val ONBOARDING_PAGE_COUNT = 3

/**
 * ViewModel for the onboarding flow (3B.12).
 *
 * Pages:
 *  0 — Welcome
 *  1 — Permissions
 *  2 — Ready (camera permission requested here)
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
) : ViewModel() {

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()

    init {
        viewModelScope.launch {
            repository.isCompleted.collect { completed ->
                _isCompleted.value = completed
            }
        }
    }

    /** Advance to the next page. On the last page, completes onboarding. */
    fun nextPage() {
        val next = _currentPage.value + 1
        if (next >= ONBOARDING_PAGE_COUNT) {
            complete()
        } else {
            _currentPage.value = next
        }
    }

    /** Skip directly to completion. */
    fun skip() {
        complete()
    }

    private fun complete() {
        viewModelScope.launch {
            repository.markCompleted()
            _isCompleted.value = true
        }
    }
}
