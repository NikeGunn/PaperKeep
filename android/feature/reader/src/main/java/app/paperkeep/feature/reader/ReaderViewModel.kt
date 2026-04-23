package app.paperkeep.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.data.repository.DocumentRepository
import app.paperkeep.core.domain.model.Page
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [ReaderScreen].
 *
 * Loads document pages from Room, tracks the current page index, zoom level,
 * and whether the OCR text overlay is visible.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: DocumentRepository,
) : ViewModel() {

    private val _pages = MutableStateFlow<List<Page>>(emptyList())
    val pages: StateFlow<List<Page>> = _pages.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _zoomLevel = MutableStateFlow(1f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _ocrOverlayEnabled = MutableStateFlow(false)
    val ocrOverlayEnabled: StateFlow<Boolean> = _ocrOverlayEnabled.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Load all pages for the given document. */
    fun loadDocument(documentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val document = repository.getDocumentById(documentId)
            _pages.value = document?.pages?.sortedBy { it.pageIndex } ?: emptyList()
            _isLoading.value = false
        }
    }

    fun onPageChanged(page: Int) {
        _currentPage.value = page.coerceIn(0, (_pages.value.size - 1).coerceAtLeast(0))
    }

    fun onZoomChanged(scale: Float) {
        _zoomLevel.value = scale.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun toggleOcrOverlay() {
        _ocrOverlayEnabled.value = !_ocrOverlayEnabled.value
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
    }
}
