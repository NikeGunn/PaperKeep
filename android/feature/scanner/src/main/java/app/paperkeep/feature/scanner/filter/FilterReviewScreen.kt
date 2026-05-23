package app.paperkeep.feature.scanner.filter

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paperkeep.core.imaging.ImageFilter
import app.paperkeep.core.imaging.ImageFilterProcessor
import app.paperkeep.feature.scanner.capture.CaptureViewModel
import app.paperkeep.feature.scanner.capture.FilterReviewState

const val TAG_FILTER_REVIEW_SCREEN = "filter_review_screen"

/**
 * Post-capture filter review screen.
 *
 * The user lands here after Google's ML Kit scanner returns cropped pages.
 * They see:
 *  - A large preview of the currently-selected page with the chosen filter
 *    applied (live re-rendered).
 *  - A horizontal page strip across the top (only if the batch has >1 page)
 *    so they can flip between pages and confirm the filter looks right on
 *    every one of them.
 *  - The full 10-effect filter strip at the bottom with thumbnail previews,
 *    each rendering the actual filter applied to a downsampled version of
 *    the current page so the user sees the real outcome before committing.
 *
 * A single Save button persists the entire batch with one filter applied to
 * every page. Cancel discards everything and pops back.
 *
 * UX rationale (FAANG-grade):
 *  - One filter applies to the entire batch. Most scanner sessions are
 *    homogeneous (you're scanning one document, not mixing receipts + art).
 *  - Live thumbnails > text labels. The user picks the look they see.
 *  - No nested dialogs, no multi-step wizard. Everything fits on one screen.
 */
@Composable
fun FilterReviewScreen(
    onSaved: (documentId: String) -> Unit,
    onCancelled: () -> Unit,
    appendToDocumentId: String? = null,
    replacePageId: String? = null,
    modifier: Modifier = Modifier,
    captureViewModel: CaptureViewModel = hiltViewModel(),
) {
    val reviewState by captureViewModel.reviewState.collectAsStateWithLifecycle()

    // If the review state is Idle (we entered the route without pending pages —
    // e.g. process death recovery), pop straight back out.
    LaunchedEffect(reviewState) {
        if (reviewState is FilterReviewState.Idle) onCancelled()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(TAG_FILTER_REVIEW_SCREEN),
    ) {
        when (val state = reviewState) {
            is FilterReviewState.Reviewing -> ReviewBody(
                state = state,
                onPageSelected = captureViewModel::selectReviewPage,
                onFilterSelected = captureViewModel::selectReviewFilter,
                onSave = {
                    captureViewModel.saveReviewedBatch(
                        appendToDocumentId = appendToDocumentId,
                        replacePageId = replacePageId,
                        onSaved = onSaved,
                    )
                },
                onCancel = {
                    captureViewModel.cancelFilterReview()
                    onCancelled()
                },
            )

            FilterReviewState.Saving -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Saving…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            FilterReviewState.Idle -> { /* LaunchedEffect handles the pop */ }
        }
    }
}

@Composable
private fun ReviewBody(
    state: FilterReviewState.Reviewing,
    onPageSelected: (Int) -> Unit,
    onFilterSelected: (ImageFilter) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    // Source = shadow-cleaned page when available, else the raw ML Kit page.
    // The cleaned version arrives shortly after the screen opens; the preview
    // re-renders automatically the moment it does (the state object's
    // identity changes).
    val currentBitmap = state.previewSourceAt(state.currentIndex)

    // Live filtered full-resolution preview. Keyed on the source bitmap
    // identity AND the selected filter so re-selecting a filter rebuilds the
    // preview, and the preview also refreshes when the cleaned source arrives.
    val previewBitmap = remember(currentBitmap, state.selectedFilter) {
        ImageFilterProcessor.apply(currentBitmap, state.selectedFilter)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top bar ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Discard scans")
            }
            Text(
                text = if (state.pages.size > 1)
                    "Page ${state.currentIndex + 1} of ${state.pages.size}"
                else "Edit scan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            Button(onClick = onSave) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save")
            }
        }

        // ── Multi-page strip (only when ≥2 pages) ─────────────────────────
        if (state.pages.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.pages) { index, page ->
                    PageThumbnail(
                        bitmap = page,
                        index = index,
                        isSelected = index == state.currentIndex,
                        onClick = { onPageSelected(index) },
                    )
                }
            }
        }

        // ── Large preview ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "Page preview with ${state.selectedFilter.label} filter",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
            )
        }

        // ── Filter strip (10 effects) ─────────────────────────────────────
        FilterStrip(
            sourceBitmap = currentBitmap,
            selectedFilter = state.selectedFilter,
            onFilterSelected = onFilterSelected,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun PageThumbnail(
    bitmap: Bitmap,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 64.dp)
            .clip(shape)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Page ${index + 1}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Tiny index badge bottom-right so the user always knows what number this is.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 10-filter strip. Each thumbnail renders the actual filter applied to a
 * downsampled copy of the current page so the user sees the real outcome.
 *
 * Thumbnails are generated once per source bitmap (memoised) — switching
 * filters is then a pure UI re-selection with no recomputation.
 */
@Composable
private fun FilterStrip(
    sourceBitmap: Bitmap,
    selectedFilter: ImageFilter,
    onFilterSelected: (ImageFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbBitmap = remember(sourceBitmap) {
        val targetW = 160
        val targetH = (targetW * sourceBitmap.height.toFloat() / sourceBitmap.width.toFloat())
            .toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true)
    }
    val filteredThumbs = remember(thumbBitmap) {
        ImageFilter.entries.associateWith { ImageFilterProcessor.apply(thumbBitmap, it) }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(ImageFilter.entries, key = { it.key }) { filter ->
            FilterTile(
                bitmap = filteredThumbs[filter] ?: thumbBitmap,
                filter = filter,
                isSelected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun FilterTile(
    bitmap: Bitmap,
    filter: ImageFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val shape = RoundedCornerShape(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp, 90.dp)
                .clip(shape)
                .border(if (isSelected) 2.dp else 1.dp, borderColor, shape),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "${filter.label} preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                )
                // Check badge top-right confirms which one is active.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Text(
            text = filter.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
