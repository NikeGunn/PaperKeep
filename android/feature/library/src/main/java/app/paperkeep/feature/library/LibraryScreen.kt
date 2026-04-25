package app.paperkeep.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.DocumentSort
import app.paperkeep.core.domain.model.Folder
import app.paperkeep.core.ui.components.DocumentCard

// ── Test tags ─────────────────────────────────────────────────────────────────
const val TAG_LIBRARY_SCREEN = "library_screen"
const val TAG_LIBRARY_GRID = "library_grid"
const val TAG_LIBRARY_EMPTY = "library_empty"
const val TAG_LIBRARY_SEARCH_BAR = "library_search_bar"
const val TAG_LIBRARY_FOLDER_CHIPS = "library_folder_chips"
const val TAG_LIBRARY_MULTI_SELECT_BAR = "library_multiselect_bar"
const val TAG_LIBRARY_SORT_BUTTON = "library_sort_button"
const val TAG_LIBRARY_DELETE_BTN = "library_delete_button"
const val TAG_LIBRARY_FAVORITE_BTN = "library_favorite_button"
const val TAG_LIBRARY_ARCHIVE_BTN = "library_archive_button"

/**
 * Main library screen: staggered grid, folder chips, search bar, multi-select
 * bottom action bar.
 *
 * Spec: §5 Phase 2 / §7.
 *  - 2-column staggered grid on phones, 4-column on tablets
 *  - Folder chips: All / Favourites / Archive + user folders
 *  - Long-press multi-select → bottom action bar (delete / favourite / archive)
 *  - Search bar collapses on scroll (DockedSearchBar on phones)
 *  - Empty state with branded CTA
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onDocumentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isTablet: Boolean = false,
    onOpenSettings: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column(modifier = Modifier.testTag(TAG_LIBRARY_SCREEN)) {
                LibraryTopBar(
                    isMultiSelect = state.isMultiSelect,
                    selectionCount = state.selectedIds.size,
                    currentSort = state.sort,
                    onClearSelection = viewModel::clearSelection,
                    onSortSelected = viewModel::setSort,
                )

                LibrarySearchBar(
                    query = state.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onClear = viewModel::clearSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag(TAG_LIBRARY_SEARCH_BAR),
                )

                FolderChipRow(
                    folders = state.folders,
                    activeFolderId = state.activeFolderId,
                    onFolderSelected = viewModel::setActiveFolder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_LIBRARY_FOLDER_CHIPS),
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = state.isMultiSelect,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                MultiSelectBottomBar(
                    selectionCount = state.selectedIds.size,
                    onDelete = viewModel::deleteSelected,
                    onToggleFavorite = viewModel::favoriteSelected,
                    onArchive = viewModel::archiveSelected,
                    modifier = Modifier.testTag(TAG_LIBRARY_MULTI_SELECT_BAR),
                )
            }
        },
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val docs = state.visibleDocuments
            if (docs.isEmpty()) {
                LibraryEmptyState(
                    isSearchActive = state.isSearchActive,
                    query = state.searchQuery,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TAG_LIBRARY_EMPTY),
                )
            } else {
                val columns = if (isTablet) 4 else 2
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(columns),
                    contentPadding = PaddingValues(12.dp),
                    verticalItemSpacing = 12.dp,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TAG_LIBRARY_GRID),
                ) {
                    items(
                        items = docs,
                        key = { it.id },
                    ) { doc ->
                        DocumentCard(
                            document = doc,
                            isSelected = doc.id in state.selectedIds,
                            onTap = {
                                if (state.isMultiSelect) viewModel.toggleSelection(doc.id)
                                else onDocumentClick(doc.id)
                            },
                            onLongPress = { viewModel.toggleSelection(doc.id) },
                        )
                    }
                }
            }
        }
    }
}

// ── Top app bar ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    isMultiSelect: Boolean,
    selectionCount: Int,
    currentSort: DocumentSort,
    onClearSelection: () -> Unit,
    onSortSelected: (DocumentSort) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = if (isMultiSelect) "$selectionCount selected" else "Documents",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            if (isMultiSelect) {
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier.semantics { contentDescription = "Clear selection" },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        },
        actions = {
            if (!isMultiSelect) {
                Box {
                    IconButton(
                        onClick = { sortMenuOpen = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(TAG_LIBRARY_SORT_BUTTON)
                            .semantics { contentDescription = "Sort documents" },
                    ) {
                        Icon(Icons.Filled.Sort, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onSortSelected(option.sort)
                                    sortMenuOpen = false
                                },
                                trailingIcon = if (currentSort == option.sort) {
                                    { Text("✓") }
                                } else null,
                            )
                        }
                    }
                }
            }
        },
    )
}

// ── Search bar ────────────────────────────────────────────────────────────────

@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.extraLarge,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        "Search documents…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.weight(1f),
        )
        if (query.isNotEmpty()) {
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(32.dp)
                    .semantics { contentDescription = "Clear search" },
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Folder chips ──────────────────────────────────────────────────────────────

private const val FOLDER_ALL = "all"
private const val FOLDER_FAVORITES = "favorites"
private const val FOLDER_ARCHIVE = "archive"

@Composable
private fun FolderChipRow(
    folders: List<Folder>,
    activeFolderId: String?,
    onFolderSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // System: All
        FolderChip(
            label = "All",
            icon = { Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(16.dp)) },
            selected = activeFolderId == null,
            onClick = { onFolderSelected(null) },
        )
        // System: Favourites
        FolderChip(
            label = "Favourites",
            icon = { Icon(Icons.Filled.Favorite, null, modifier = Modifier.size(16.dp)) },
            selected = activeFolderId == FOLDER_FAVORITES,
            onClick = { onFolderSelected(FOLDER_FAVORITES) },
        )
        // System: Archive
        FolderChip(
            label = "Archive",
            icon = { Icon(Icons.Filled.Archive, null, modifier = Modifier.size(16.dp)) },
            selected = activeFolderId == FOLDER_ARCHIVE,
            onClick = { onFolderSelected(FOLDER_ARCHIVE) },
        )
        // User folders
        folders.forEach { folder ->
            FolderChip(
                label = folder.name,
                icon = { Icon(Icons.Filled.Folder, null, modifier = Modifier.size(16.dp)) },
                selected = activeFolderId == folder.id,
                onClick = { onFolderSelected(folder.id) },
            )
        }
    }
}

@Composable
private fun FolderChip(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = icon,
        modifier = modifier.semantics { contentDescription = "Filter: $label" },
    )
}

// ── Multi-select bottom bar ───────────────────────────────────────────────────

@Composable
private fun MultiSelectBottomBar(
    selectionCount: Int,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(
        modifier = modifier,
        actions = {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(TAG_LIBRARY_FAVORITE_BTN)
                    .semantics { contentDescription = "Toggle favourite" },
            ) {
                Icon(Icons.Filled.FavoriteBorder, contentDescription = null)
            }
            IconButton(
                onClick = onArchive,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(TAG_LIBRARY_ARCHIVE_BTN)
                    .semantics { contentDescription = "Archive selected" },
            ) {
                Icon(Icons.Filled.Archive, contentDescription = null)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onDelete,
                modifier = Modifier
                    .testTag(TAG_LIBRARY_DELETE_BTN)
                    .semantics { contentDescription = "Delete $selectionCount selected" },
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
            }
        },
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private enum class SortOption(val label: String, val sort: DocumentSort) {
    NEWEST("Newest first", DocumentSort.NEWEST),
    OLDEST("Oldest first", DocumentSort.OLDEST),
    TITLE("Title A–Z", DocumentSort.TITLE_AZ),
    PAGES("Most pages", DocumentSort.MOST_PAGES),
}

@Composable
private fun LibraryEmptyState(
    isSearchActive: Boolean,
    query: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isSearchActive) {
            Text(
                text = "No results for \"$query\"",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Text(
                text = "Try different keywords",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                text = "No documents yet",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Tap the camera button to scan your first document",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
