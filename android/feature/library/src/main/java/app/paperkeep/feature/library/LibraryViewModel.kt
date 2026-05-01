package app.paperkeep.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.paperkeep.core.common.DebugLog
import app.paperkeep.core.data.repository.DocumentRepository
import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.DocumentSort
import app.paperkeep.core.domain.model.Folder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val FOLDER_FAVORITES = "favorites"
private const val FOLDER_ARCHIVE = "archive"

data class LibraryUiState(
    val documents: List<Document> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val sort: DocumentSort = DocumentSort.NEWEST,
    val selectedIds: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    val activeFolderId: String? = null,
    val searchQuery: String = "",
    val searchResults: List<Document> = emptyList(),
    val isSearchActive: Boolean = false,
) {
    val isMultiSelect: Boolean get() = selectedIds.isNotEmpty()

    /** Documents shown in the current view: search results when active, full list otherwise. */
    val visibleDocuments: List<Document>
        get() = if (isSearchActive) searchResults else documents
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: DocumentRepository,
) : ViewModel() {

    private val _sort = MutableStateFlow(DocumentSort.NEWEST)
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isRefreshing = MutableStateFlow(false)
    private val _activeFolderId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<Document>>(emptyList())
    private val _isSearchActive = MutableStateFlow(false)

    private val documents: StateFlow<List<Document>> = combine(
        _sort,
        _activeFolderId,
    ) { sort, folderId -> sort to folderId }
        .flatMapLatest { (sort, folderId) ->
            when (folderId) {
                null -> repo.observeDocuments(sort)
                FOLDER_FAVORITES -> repo.observeFavorites()
                FOLDER_ARCHIVE -> repo.observeArchived()
                else -> repo.observeByFolder(folderId)
            }
        }
        .onEach { docs ->
            DebugLog.d(
                "Paperkeep.Library",
                "observed ${docs.size} docs; " +
                    "first=${docs.firstOrNull()?.let { "${it.id} pages=${it.pages.size} " +
                        "thumb=${it.pages.firstOrNull()?.encryptedThumbPath}" }}",
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val folders: StateFlow<List<Folder>> = repo.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<LibraryUiState> = combine(
        documents,
        folders,
        _sort,
        _selectedIds,
        _isRefreshing,
        _activeFolderId,
        _searchQuery,
        _searchResults,
        _isSearchActive,
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        LibraryUiState(
            documents = arr[0] as List<Document>,
            folders = arr[1] as List<Folder>,
            sort = arr[2] as DocumentSort,
            selectedIds = arr[3] as Set<String>,
            isRefreshing = arr[4] as Boolean,
            activeFolderId = arr[5] as String?,
            searchQuery = arr[6] as String,
            searchResults = arr[7] as List<Document>,
            isSearchActive = arr[8] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        observeSearchQuery()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        _searchQuery
            .debounce(300)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.isBlank()) {
                    _searchResults.value = emptyList()
                    _isSearchActive.value = false
                } else {
                    _isSearchActive.value = true
                    // Merge title-FTS results with HMAC'd OCR-FTS results (de-duplicated by id)
                    val titleResults = repo.searchDocuments(query)
                    val ocrResults = repo.searchByOcrContent(query)
                    val merged = (titleResults + ocrResults)
                        .distinctBy { it.id }
                        .sortedByDescending { it.createdAt }
                    _searchResults.value = merged
                }
            }
            .launchIn(viewModelScope)
    }

    fun setSort(sort: DocumentSort) {
        _sort.value = sort
    }

    fun toggleSelection(id: String) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            kotlinx.coroutines.delay(300)
            _isRefreshing.value = false
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val ids = _selectedIds.value.toList()
            ids.forEach { repo.deleteDocumentById(it) }
            clearSelection()
        }
    }

    fun favoriteSelected() {
        viewModelScope.launch {
            val ids = _selectedIds.value.toList()
            val currentDocs = documents.value
            ids.forEach { id ->
                val doc = currentDocs.firstOrNull { it.id == id } ?: return@forEach
                repo.setFavorite(id, !doc.isFavorite)
            }
            clearSelection()
        }
    }

    fun archiveSelected() {
        viewModelScope.launch {
            val ids = _selectedIds.value.toList()
            ids.forEach { repo.setArchived(it, true) }
            clearSelection()
        }
    }

    fun moveSelectedToFolder(folderId: String?) {
        viewModelScope.launch {
            _selectedIds.value.forEach { id ->
                repo.moveDocumentToFolder(id, folderId)
            }
            clearSelection()
        }
    }

    fun setActiveFolder(folderId: String?) {
        _activeFolderId.value = folderId
        clearSelection()
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val folder = Folder(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                icon = "folder",
                autoRule = null,
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
            )
            repo.createFolder(folder)
        }
    }

    fun renameFolder(folder: Folder, newName: String) {
        viewModelScope.launch {
            repo.updateFolder(folder.copy(name = newName, updatedAt = java.time.Instant.now()))
        }
    }

    /**
     * Update the auto-rule for a folder. Pass null to remove the rule.
     * After updating, re-applies rules to all currently unfiled documents.
     */
    fun setFolderAutoRule(folder: Folder, autoRule: String?) {
        viewModelScope.launch {
            repo.updateFolder(folder.copy(autoRule = autoRule, updatedAt = java.time.Instant.now()))
            // Apply the new rule to existing unfiled documents
            val unfiled = repo.observeDocuments(app.paperkeep.core.domain.model.DocumentSort.NEWEST).first()
            unfiled.filter { it.folderId == null && it.docType != null }.forEach { doc ->
                repo.applyAutoRules(doc.id)
            }
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            repo.deleteFolder(folder)
            if (_activeFolderId.value == folder.id) {
                _activeFolderId.value = null
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearchActive.value = false
    }
}
