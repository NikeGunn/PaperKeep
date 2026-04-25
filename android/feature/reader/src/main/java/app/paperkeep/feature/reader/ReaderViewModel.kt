package app.paperkeep.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.data.repository.DocumentRepository
import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.Page
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Possible one-shot events emitted to the UI. */
sealed interface ReaderEvent {
    data class SharePage(val pageId: String) : ReaderEvent
    data class ExportDocument(val documentId: String) : ReaderEvent
    object DocumentDeleted : ReaderEvent
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: DocumentRepository,
) : ViewModel() {

    private val _document = MutableStateFlow<Document?>(null)

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

    private val _showBottomBar = MutableStateFlow(true)
    val showBottomBar: StateFlow<Boolean> = _showBottomBar.asStateFlow()

    private val _isRenaming = MutableStateFlow(false)
    val isRenaming: StateFlow<Boolean> = _isRenaming.asStateFlow()

    private val _documentTitle = MutableStateFlow("")
    val documentTitle: StateFlow<String> = _documentTitle.asStateFlow()

    private val _event = MutableStateFlow<ReaderEvent?>(null)
    val event: StateFlow<ReaderEvent?> = _event.asStateFlow()

    fun loadDocument(documentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val document = repository.getDocumentById(documentId)
            _document.value = document
            _documentTitle.value = document?.title ?: ""
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

    fun toggleBottomBar() {
        _showBottomBar.value = !_showBottomBar.value
    }

    fun startRename() {
        _isRenaming.value = true
    }

    fun cancelRename() {
        _isRenaming.value = false
        _documentTitle.value = _document.value?.title ?: ""
    }

    fun onTitleChanged(title: String) {
        _documentTitle.value = title
    }

    fun commitRename() {
        val docId = _document.value?.id ?: return
        val newTitle = _documentTitle.value.trim()
        if (newTitle.isBlank()) {
            cancelRename()
            return
        }
        _isRenaming.value = false
        viewModelScope.launch {
            repository.updateTitle(docId, newTitle)
            _document.value = _document.value?.copy(title = newTitle)
        }
    }

    fun deleteCurrentPage() {
        val page = _pages.value.getOrNull(_currentPage.value) ?: return
        viewModelScope.launch {
            repository.deleteDocumentById(page.documentId)
            _event.value = ReaderEvent.DocumentDeleted
        }
    }

    fun deleteDocument() {
        val docId = _document.value?.id ?: return
        viewModelScope.launch {
            repository.deleteDocumentById(docId)
            _event.value = ReaderEvent.DocumentDeleted
        }
    }

    fun shareCurrentPage() {
        val page = _pages.value.getOrNull(_currentPage.value) ?: return
        _event.value = ReaderEvent.SharePage(page.id)
    }

    fun requestExport() {
        val docId = _document.value?.id ?: return
        _event.value = ReaderEvent.ExportDocument(docId)
    }

    fun consumeEvent() {
        _event.value = null
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
    }
}
