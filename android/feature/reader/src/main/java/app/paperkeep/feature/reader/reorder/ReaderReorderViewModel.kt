package app.paperkeep.feature.reader.reorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.common.DebugLog
import app.paperkeep.core.data.repository.DocumentRepository
import app.paperkeep.core.domain.model.Page
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the reorder screen state. Loads the document's pages, exposes them as
 * a mutable list ordered by [Page.pageIndex], lets the UI swap any two
 * indices, and persists the new ordering on save.
 */
@HiltViewModel
class ReaderReorderViewModel @Inject constructor(
    private val repository: DocumentRepository,
) : ViewModel() {

    private val _pages = MutableStateFlow<List<Page>>(emptyList())
    val pages: StateFlow<List<Page>> = _pages.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private var documentId: String = ""

    fun load(documentId: String) {
        this.documentId = documentId
        viewModelScope.launch {
            _isBusy.value = true
            val doc = repository.getDocumentById(documentId)
            _pages.value = doc?.pages?.sortedBy { it.pageIndex } ?: emptyList()
            _isBusy.value = false
        }
    }

    /** Move the page at [fromIndex] to [toIndex] (both in current visible order). */
    fun moveTo(fromIndex: Int, toIndex: Int) {
        val current = _pages.value.toMutableList()
        if (fromIndex !in current.indices) return
        val clamped = toIndex.coerceIn(0, current.size - 1)
        if (fromIndex == clamped) return
        val item = current.removeAt(fromIndex)
        current.add(clamped, item)
        _pages.value = current
    }

    /** Persist the current visible order. */
    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val orderedIds = _pages.value.map { it.id }
                repository.reorderPages(documentId, orderedIds)
                _saved.value = true
                onDone()
            } catch (e: Throwable) {
                DebugLog.e("Paperkeep.Reorder", "save failed", e)
            } finally {
                _isBusy.value = false
            }
        }
    }
}
