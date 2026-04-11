package com.scanvault.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanvault.core.domain.model.DocumentSort
import com.scanvault.core.ui.components.DocumentCard

/**
 * Library screen.
 *
 * Spec (FRONTEND_MVP.md Phase 2 §2):
 * - 2-column grid on phones, 4 columns on tablets
 * - DocumentCard: thumbnail, title (titleMedium), page count + date (bodyMedium in onSurfaceVariant)
 * - Long-press → multi-select mode with batch delete
 * - Sort: newest / oldest / A–Z / most pages
 * - Empty state with CTA
 * - Pull-to-refresh
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onDocumentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isTablet: Boolean = false,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val columns = if (isTablet) 4 else 2

    Scaffold(
        topBar = {
            LibraryTopBar(
                isMultiSelect = state.isMultiSelect,
                selectionCount = state.selectedIds.size,
                currentSort = state.sort,
                onClearSelection = viewModel::clearSelection,
                onSortSelected = viewModel::setSort,
                onDeleteSelected = viewModel::deleteSelected,
            )
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
            if (state.documents.isEmpty()) {
                LibraryEmptyState(
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = state.documents,
                        key = { it.id },
                    ) { doc ->
                        DocumentCard(
                            document = doc,
                            isSelected = doc.id in state.selectedIds,
                            onTap = {
                                if (state.isMultiSelect) {
                                    viewModel.toggleSelection(doc.id)
                                } else {
                                    onDocumentClick(doc.id)
                                }
                            },
                            onLongPress = { viewModel.toggleSelection(doc.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    isMultiSelect: Boolean,
    selectionCount: Int,
    currentSort: DocumentSort,
    onClearSelection: () -> Unit,
    onSortSelected: (DocumentSort) -> Unit,
    onDeleteSelected: () -> Unit,
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
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                }
            }
        },
        actions = {
            if (isMultiSelect) {
                IconButton(onClick = onDeleteSelected) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                }
            } else {
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = "Sort documents")
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

private enum class SortOption(val label: String, val sort: DocumentSort) {
    NEWEST("Newest first", DocumentSort.NEWEST),
    OLDEST("Oldest first", DocumentSort.OLDEST),
    TITLE("Title A–Z", DocumentSort.TITLE_AZ),
    PAGES("Most pages", DocumentSort.MOST_PAGES),
}

@Composable
private fun LibraryEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
