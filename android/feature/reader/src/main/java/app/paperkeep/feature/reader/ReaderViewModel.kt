package app.paperkeep.feature.reader

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.common.DebugLog
import app.paperkeep.core.data.repository.DocumentRepository
import app.paperkeep.core.data.share.SharePayloadBuilder
import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.Page
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Format the user picks from the share / download bottom sheet. */
enum class ShareFormat { PDF, IMAGE, TEXT }

/** Possible one-shot events emitted to the UI. */
sealed interface ReaderEvent {
    /** System share sheet — single URI. */
    data class ShareIntent(
        val uri: Uri,
        val mimeType: String,
        val displayName: String,
    ) : ReaderEvent

    /** System share sheet — multiple URIs (multi-page image share). */
    data class ShareMultipleIntent(
        val uris: List<Uri>,
        val mimeType: String,
    ) : ReaderEvent

    /**
     * SAF "Save to..." picker. The screen launches ACTION_CREATE_DOCUMENT
     * with [suggestedName] + [mimeType]; on result the screen calls
     * [ReaderViewModel.writeExportTo] to copy bytes into the picked URI.
     */
    data class StartSafExport(
        val suggestedName: String,
        val mimeType: String,
        val sourceFilePath: String,
    ) : ReaderEvent

    data class Toast(val message: String) : ReaderEvent

    object DocumentDeleted : ReaderEvent
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val sharePayloadBuilder: SharePayloadBuilder,
    private val dispatchers: AppDispatchers,
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

    /** Bottom sheet visibility — share vs download mode. */
    private val _formatSheetMode = MutableStateFlow<FormatSheetMode?>(null)
    val formatSheetMode: StateFlow<FormatSheetMode?> = _formatSheetMode.asStateFlow()

    /** Set true while a share/export payload is being built. */
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

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

    /** Re-fetch — used after returning from add-page flow. */
    fun refresh() {
        val id = _document.value?.id ?: return
        loadDocument(id)
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

    fun deleteDocument() {
        val docId = _document.value?.id ?: return
        viewModelScope.launch {
            repository.deleteDocumentById(docId)
            _event.value = ReaderEvent.DocumentDeleted
        }
    }

    // ── Format sheet wiring ─────────────────────────────────────────────────

    fun openShareSheet() {
        if (_pages.value.isEmpty()) return
        _formatSheetMode.value = FormatSheetMode.SHARE
    }

    fun openDownloadSheet() {
        if (_pages.value.isEmpty()) return
        _formatSheetMode.value = FormatSheetMode.DOWNLOAD
    }

    fun dismissFormatSheet() {
        _formatSheetMode.value = null
    }

    /** User picked a format from the bottom sheet. */
    fun onFormatPicked(format: ShareFormat) {
        val mode = _formatSheetMode.value ?: return
        _formatSheetMode.value = null
        when (mode) {
            FormatSheetMode.SHARE -> share(format)
            FormatSheetMode.DOWNLOAD -> download(format)
        }
    }

    private fun share(format: ShareFormat) {
        val document = _document.value ?: return
        val pageIdx = _currentPage.value
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (format) {
                    ShareFormat.PDF -> {
                        val payload = sharePayloadBuilder.buildPdf(document)
                        _event.value = ReaderEvent.ShareIntent(
                            uri = payload.uri,
                            mimeType = payload.mimeType,
                            displayName = payload.displayName,
                        )
                    }
                    ShareFormat.IMAGE -> {
                        if (document.pages.size > 1) {
                            val multi = sharePayloadBuilder.buildAllPagesJpeg(document)
                            _event.value = ReaderEvent.ShareMultipleIntent(
                                uris = multi.uris,
                                mimeType = multi.mimeType,
                            )
                        } else {
                            val payload = sharePayloadBuilder.buildPageJpeg(document, pageIdx)
                            _event.value = ReaderEvent.ShareIntent(
                                uri = payload.uri,
                                mimeType = payload.mimeType,
                                displayName = payload.displayName,
                            )
                        }
                    }
                    ShareFormat.TEXT -> {
                        val payload = sharePayloadBuilder.buildOcrText(document)
                        if (payload == null) {
                            _event.value = ReaderEvent.Toast(
                                "No text recognized yet — try again in a moment."
                            )
                            return@launch
                        }
                        _event.value = ReaderEvent.ShareIntent(
                            uri = payload.uri,
                            mimeType = payload.mimeType,
                            displayName = payload.displayName,
                        )
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("Paperkeep.Share", "share failed", e)
                _event.value = ReaderEvent.Toast("Share failed: ${e.message ?: "unknown error"}")
            } finally {
                _isBusy.value = false
            }
        }
    }

    private fun download(format: ShareFormat) {
        val document = _document.value ?: return
        val pageIdx = _currentPage.value
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (format) {
                    ShareFormat.PDF -> {
                        val payload = sharePayloadBuilder.buildPdf(document)
                        _event.value = ReaderEvent.StartSafExport(
                            suggestedName = payload.displayName,
                            mimeType = payload.mimeType,
                            sourceFilePath = payload.file.absolutePath,
                        )
                    }
                    ShareFormat.IMAGE -> {
                        // For multi-page, save the current page only — SAF
                        // doesn't support multi-file create in one prompt.
                        // The bottom sheet copy reflects this.
                        val payload = sharePayloadBuilder.buildPageJpeg(document, pageIdx)
                        _event.value = ReaderEvent.StartSafExport(
                            suggestedName = payload.displayName,
                            mimeType = payload.mimeType,
                            sourceFilePath = payload.file.absolutePath,
                        )
                    }
                    ShareFormat.TEXT -> {
                        val payload = sharePayloadBuilder.buildOcrText(document)
                        if (payload == null) {
                            _event.value = ReaderEvent.Toast(
                                "No text recognized yet — try again in a moment."
                            )
                            return@launch
                        }
                        _event.value = ReaderEvent.StartSafExport(
                            suggestedName = payload.displayName,
                            mimeType = payload.mimeType,
                            sourceFilePath = payload.file.absolutePath,
                        )
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("Paperkeep.Share", "download prep failed", e)
                _event.value = ReaderEvent.Toast(
                    "Couldn't prepare download: ${e.message ?: "unknown error"}"
                )
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Called by [ReaderScreen] after the SAF picker resolves to [destUri].
     * Copies the previously-built payload bytes into [destUri].
     */
    fun writeExportTo(destUri: Uri, sourceFilePath: String, openOutput: (Uri) -> java.io.OutputStream?) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val source = java.io.File(sourceFilePath)
                if (!source.exists()) {
                    _event.value = ReaderEvent.Toast("Export file expired — try again.")
                    return@launch
                }
                withContext(dispatchers.io) {
                    val out = openOutput(destUri) ?: error("Could not open output for $destUri")
                    out.use { stream ->
                        source.inputStream().use { input -> input.copyTo(stream) }
                    }
                }
                _event.value = ReaderEvent.Toast("Saved.")
            } catch (e: Exception) {
                DebugLog.e("Paperkeep.Share", "writeExportTo failed", e)
                _event.value = ReaderEvent.Toast("Save failed: ${e.message ?: "unknown error"}")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun consumeEvent() {
        _event.value = null
    }

    enum class FormatSheetMode { SHARE, DOWNLOAD }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
    }
}
