package app.paperkeep.feature.reader.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.common.DebugLog
import app.paperkeep.core.data.crypto.EncryptedImageStore
import app.paperkeep.core.data.repository.DocumentRepository
import app.paperkeep.core.imaging.ImageFilter
import app.paperkeep.core.imaging.ImageFilterProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns the Edit-mode state for the reader (Feature 1).
 *
 * Responsibilities:
 *  - Toggle Edit mode on/off
 *  - Apply one of the 10 [EditTool]s to a target page
 *  - For unimplemented tools, surface the "coming soon" sheet
 *  - For Filter: re-render the page bitmap through [ImageFilterProcessor]
 *    and overwrite the encrypted file on disk
 *  - For Page Title: edit and persist a per-page title
 *  - For Reorder / Crop / Retake: emit a navigation event for the screen to
 *    consume (those tools live in other modules)
 *
 * Undo: maintains a bounded stack of [EditAction]s. Filter and page-title
 * changes are reversible by re-writing the previous value. Navigation-driven
 * tools (Reorder, Crop, Retake) are not undoable from this layer because
 * they hand off to a separate flow.
 */
@HiltViewModel
class ReaderEditViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val imageStore: EncryptedImageStore,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    sealed interface EditEvent {
        /** Open Reorder screen for [documentId]. */
        data class OpenReorder(val documentId: String) : EditEvent
        /** Re-launch crop on [pageId]. */
        data class OpenCrop(val documentId: String, val pageId: String) : EditEvent
        /** Launch scanner in "replace this page" mode. */
        data class OpenRetake(val documentId: String, val pageId: String) : EditEvent
        /** Show the page-title rename dialog for [pageId]. */
        data class PromptPageTitle(val pageId: String, val current: String?) : EditEvent
        /** Show the filter chooser for [pageId]. */
        data class PromptFilter(val pageId: String, val current: ImageFilter) : EditEvent
        /** Show "coming soon" sheet for [tool]. */
        data class ComingSoon(val tool: EditTool) : EditEvent
        /** Generic snackbar. */
        data class Toast(val message: String) : EditEvent
    }

    /** A reversible edit action retained in the undo stack. */
    sealed interface EditAction {
        data class FilterChange(val pageId: String, val previous: String, val next: String) : EditAction
        data class TitleChange(val pageId: String, val previous: String?, val next: String?) : EditAction
    }

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _event = MutableStateFlow<EditEvent?>(null)
    val event: StateFlow<EditEvent?> = _event.asStateFlow()

    private val undoStack = ArrayDeque<EditAction>()
    private val _undoAvailable = MutableStateFlow(false)
    val undoAvailable: StateFlow<Boolean> = _undoAvailable.asStateFlow()

    fun toggleEditMode() {
        _editMode.value = !_editMode.value
    }

    fun exitEditMode() { _editMode.value = false }

    fun consumeEvent() { _event.value = null }

    /**
     * Dispatch a tool tap. [currentPageId] is the page the toolbar should
     * act on (the page closest to the viewport center). Caller is
     * responsible for not invoking when [currentPageId] is null (single-tap
     * tools won't make sense without a target).
     */
    fun onToolPicked(tool: EditTool, documentId: String, currentPageId: String?, currentFilter: ImageFilter, currentTitle: String?) {
        if (!tool.implemented) {
            _event.value = EditEvent.ComingSoon(tool)
            return
        }
        when (tool) {
            EditTool.REORDER_PAGES -> _event.value = EditEvent.OpenReorder(documentId)
            EditTool.CROP -> currentPageId?.let { _event.value = EditEvent.OpenCrop(documentId, it) }
            EditTool.FILTER -> currentPageId?.let { _event.value = EditEvent.PromptFilter(it, currentFilter) }
            EditTool.RETAKE -> currentPageId?.let { _event.value = EditEvent.OpenRetake(documentId, it) }
            EditTool.PAGE_TITLE -> currentPageId?.let { _event.value = EditEvent.PromptPageTitle(it, currentTitle) }
            // Unreachable — guarded by `implemented` above.
            else -> _event.value = EditEvent.ComingSoon(tool)
        }
    }

    // ── Filter swap ─────────────────────────────────────────────────────────

    /**
     * Apply [newFilter] to the encrypted image at [encryptedImagePath] and
     * persist the new filter key on the page row. The original bitmap is
     * re-read from disk, transformed, and re-encrypted in place.
     *
     * The previous filter key is captured for undo so the user can revert.
     */
    fun applyFilter(
        pageId: String,
        encryptedImagePath: String,
        previousFilterKey: String,
        newFilter: ImageFilter,
    ) {
        if (newFilter.key == previousFilterKey) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                withContext(dispatchers.io) {
                    val file = java.io.File(encryptedImagePath)
                    val plain = imageStore.read(file)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(plain, 0, plain.size)
                        ?: error("Failed to decode page bitmap")
                    val out = ImageFilterProcessor.apply(bitmap, newFilter)
                    val baos = java.io.ByteArrayOutputStream()
                    out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, baos)
                    imageStore.write(file, baos.toByteArray())
                }
                repository.setPageFilter(pageId, newFilter.key)
                pushUndo(EditAction.FilterChange(pageId, previousFilterKey, newFilter.key))
                _event.value = EditEvent.Toast("Filter applied")
            } catch (e: Throwable) {
                DebugLog.e("Paperkeep.Edit", "applyFilter failed", e)
                _event.value = EditEvent.Toast("Couldn't apply filter")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // ── Page title ──────────────────────────────────────────────────────────

    fun setPageTitle(pageId: String, previous: String?, next: String?) {
        val normalisedPrev = previous?.trim()?.ifBlank { null }
        val normalisedNext = next?.trim()?.ifBlank { null }
        if (normalisedPrev == normalisedNext) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.setPageTitle(pageId, normalisedNext)
                pushUndo(EditAction.TitleChange(pageId, normalisedPrev, normalisedNext))
                _event.value = EditEvent.Toast(
                    if (normalisedNext == null) "Title cleared" else "Title saved"
                )
            } catch (e: Throwable) {
                DebugLog.e("Paperkeep.Edit", "setPageTitle failed", e)
                _event.value = EditEvent.Toast("Couldn't save title")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // ── Undo ────────────────────────────────────────────────────────────────

    private fun pushUndo(action: EditAction) {
        undoStack.addLast(action)
        while (undoStack.size > MAX_UNDO_DEPTH) undoStack.removeFirst()
        _undoAvailable.value = undoStack.isNotEmpty()
    }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        _undoAvailable.value = undoStack.isNotEmpty()
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (action) {
                    is EditAction.FilterChange -> {
                        // For filter we revert the DB key only; the on-disk image
                        // already reflects the *applied* filter. A true visual
                        // undo would require keeping the original bitmap around,
                        // which we don't to save storage.
                        repository.setPageFilter(action.pageId, action.previous)
                        _event.value = EditEvent.Toast("Filter reverted (visual change kept)")
                    }
                    is EditAction.TitleChange -> {
                        repository.setPageTitle(action.pageId, action.previous)
                        _event.value = EditEvent.Toast("Title reverted")
                    }
                }
            } catch (e: Throwable) {
                DebugLog.e("Paperkeep.Edit", "undo failed", e)
                _event.value = EditEvent.Toast("Undo failed")
            } finally {
                _isBusy.value = false
            }
        }
    }

    companion object {
        const val MAX_UNDO_DEPTH = 30
    }
}
