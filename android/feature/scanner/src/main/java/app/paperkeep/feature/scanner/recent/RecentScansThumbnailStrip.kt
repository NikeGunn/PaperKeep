package app.paperkeep.feature.scanner.recent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paperkeep.core.data.db.ScanEntity

const val TAG_THUMBNAIL_STRIP = "recent_scans_thumbnail_strip"
const val TAG_THUMBNAIL_EMPTY = "recent_scans_empty_state"
const val TAG_THUMBNAIL_ITEM = "recent_scan_thumbnail_item"

/**
 * Horizontal thumbnail strip at the bottom of the camera screen (1B.17).
 * Shows up to the last 10 scans from Room.
 * Shows an empty-state placeholder when no scans exist yet.
 */
@Composable
fun RecentScansThumbnailStrip(
    onOpenLibrary: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RecentScansViewModel = hiltViewModel(),
) {
    val scans by viewModel.recentScans.collectAsStateWithLifecycle()
    RecentScansThumbnailStripContent(
        scans = scans,
        onOpenLibrary = onOpenLibrary,
        modifier = modifier,
    )
}

/** Stateless content — directly testable without DI. */
@Composable
fun RecentScansThumbnailStripContent(
    scans: List<ScanEntity>,
    onOpenLibrary: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .testTag(TAG_THUMBNAIL_STRIP),
    ) {
        if (scans.isEmpty()) {
            Text(
                text = "No scans yet",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable(onClick = onOpenLibrary)
                    .testTag(TAG_THUMBNAIL_EMPTY)
                    .semantics { contentDescription = "No recent scans. Open library" },
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(items = scans, key = { it.id }) { scan ->
                    ThumbnailItem(scan = scan, onOpenLibrary = onOpenLibrary)
                }
            }
        }
    }
}

@Composable
private fun ThumbnailItem(
    scan: ScanEntity,
    onOpenLibrary: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpenLibrary)
            .testTag("${TAG_THUMBNAIL_ITEM}_${scan.id}")
            .semantics { contentDescription = "Scan thumbnail: ${scan.title}. Open library" },
        contentAlignment = Alignment.Center,
    ) {
        // In Phase 2, this will be replaced by an encrypted thumbnail loaded via Coil.
        // For Phase 1, render a placeholder with the scan title initial.
        Text(
            text = scan.title.firstOrNull()?.uppercase() ?: "S",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
