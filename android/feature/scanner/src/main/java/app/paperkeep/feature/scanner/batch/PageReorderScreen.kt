package app.paperkeep.feature.scanner.batch

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image

/**
 * Page reorder screen for multi-page batch capture (2B.5).
 *
 * Displays captured pages in a 2-column grid. The user can:
 *  - Long-press and drag a card to reorder pages.
 *  - Tap the × badge on a card to delete that page.
 *  - Tap "Add more" to return to the camera (handled by [onAddMore]).
 *  - Tap "Done" to confirm and continue (handled by [onConfirm]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PageReorderScreen(
    onNavigateBack: () -> Unit,
    onAddMore: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BatchCaptureViewModel = hiltViewModel(),
) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()

    // Track the index being dragged for visual feedback
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${pages.size} page${if (pages.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = "Navigate back" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onAddMore,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Add more pages" },
                ) {
                    Text("Add page")
                }
                Button(
                    onClick = onConfirm,
                    enabled = pages.isNotEmpty(),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Confirm pages" },
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text("Done", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    ) { innerPadding ->
        if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No pages yet",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Tap \"Add page\" to capture pages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                itemsIndexed(
                    items = pages,
                    key = { _, page -> page.id },
                ) { index, page ->
                    PageThumbnailCard(
                        page = page,
                        pageNumber = index + 1,
                        isBeingDragged = index == draggingIndex,
                        isDropTarget = index == hoveredIndex && draggingIndex != -1,
                        onDelete = { viewModel.deletePage(page.id) },
                        onDragStart = { draggingIndex = index },
                        onDragEnd = {
                            if (draggingIndex != -1 && hoveredIndex != -1) {
                                viewModel.movePage(draggingIndex, hoveredIndex)
                            }
                            draggingIndex = -1
                            hoveredIndex = -1
                        },
                        onDragOver = { hoveredIndex = index },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageThumbnailCard(
    page: BatchPage,
    pageNumber: Int,
    isBeingDragged: Boolean,
    isDropTarget: Boolean,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(0.707f) // A4 aspect ratio
            .clip(MaterialTheme.shapes.medium)
            .background(
                when {
                    isDropTarget -> MaterialTheme.colorScheme.primaryContainer
                    isBeingDragged -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .pointerInput(page.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { _, _ -> onDragOver() },
                )
            }
            .semantics { contentDescription = "Page $pageNumber" },
    ) {
        Image(
            bitmap = page.bitmap.asImageBitmap(),
            contentDescription = "Page $pageNumber thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Page number badge (bottom-left)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$pageNumber",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        // Delete button (top-right)
        SmallFloatingActionButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .semantics { contentDescription = "Delete page $pageNumber" },
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
