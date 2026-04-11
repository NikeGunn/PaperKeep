package com.scanvault.feature.scanner.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanvault.core.data.db.ScanDao
import com.scanvault.core.data.db.ScanEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Provides the recent scans (last 10) for the thumbnail strip on the camera screen (1B.17).
 * Observes Room — automatically updates when a new scan is saved.
 */
@HiltViewModel
class RecentScansViewModel @Inject constructor(
    scanDao: ScanDao,
) : ViewModel() {

    val recentScans: StateFlow<List<ScanEntity>> = scanDao
        .observeRecent(limit = 10)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
