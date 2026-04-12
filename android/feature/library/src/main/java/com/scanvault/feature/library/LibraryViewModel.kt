package com.scanvault.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanvault.core.data.repository.DocumentRepository
import com.scanvault.core.domain.model.Document
import com.scanvault.core.domain.model.DocumentSort
import com.scanvault.core.domain.model.Folder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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
    val visibleDocuments: List<Document> get() = if (isSearchActive) searchResults else documents
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

    private val documents: StateFlow<List<Document>> = _sort
        .flatMapLatest { sort -> repo.observeDocuments(sort) }
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
                    _searchResults.value = repo.searchDocuments(query)
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
        // Room is reactive — just toggle the refreshing indicator briefly.
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
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val folder = Folder(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
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
