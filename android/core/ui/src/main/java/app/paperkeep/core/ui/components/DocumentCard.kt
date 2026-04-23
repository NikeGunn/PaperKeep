package app.paperkeep.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.SyncStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private data class SyncIconSpec(val icon: ImageVector, val tint: Color, val description: String)

@Composable
private fun syncIconSpec(status: SyncStatus): SyncIconSpec {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        SyncStatus.CLOUD_DONE -> SyncIconSpec(Icons.Filled.CloudDone, scheme.primary, "Synced")
        SyncStatus.UPLOADING  -> SyncIconSpec(Icons.Filled.CloudUpload, scheme.tertiary, "Uploading")
        SyncStatus.PENDING    -> SyncIconSpec(Icons.Filled.CloudSync, scheme.onSurfaceVariant, "Pending sync")
        SyncStatus.LOCAL_ONLY -> SyncIconSpec(Icons.Filled.CloudOff, scheme.onSurfaceVariant.copy(alpha = 0.5f), "Local only")
    }
}

private val cardShape = RoundedCornerShape(16.dp)
private val dateFormatter = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .withZone(ZoneId.systemDefault())

/**
 * A document card for the library grid.
 *
 * Rules (DESIGN_SYSTEM.md §2.7):
 * - 16dp corner radius
 * - 1dp outline in outlineVariant (no drop shadow)
 * - Title: titleMedium, max 1 line, ellipsis
 * - Metadata: bodyMedium in onSurfaceVariant
 * - Thumbnail: actual aspect ratio, never stretched
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentCard(
    document: Document,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outlineColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .clip(cardShape)
            .border(borderWidth, outlineColor, cardShape)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
            )
            .semantics {
                val syncDesc = when (document.syncStatus) {
                    SyncStatus.CLOUD_DONE -> "synced"
                    SyncStatus.UPLOADING -> "uploading"
                    SyncStatus.PENDING -> "pending sync"
                    SyncStatus.LOCAL_ONLY -> "local only"
                }
                contentDescription = "Document: ${document.title}, ${document.pageCount} pages, $syncDesc"
            },
    ) {
        Column {
            // Thumbnail
            val thumbPath = document.pages.firstOrNull()?.thumbPath
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = thumbPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                // Selection overlay
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    )
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp),
                    )
                }
                // Color tag
                document.colorTag?.let { argb ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(argb)),
                    )
                }
                // Sync status icon — bottom-end corner
                val syncSpec = syncIconSpec(document.syncStatus)
                Icon(
                    imageVector = syncSpec.icon,
                    contentDescription = syncSpec.description,
                    tint = syncSpec.tint,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(16.dp),
                )
            }

            // Title + metadata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = dateFormatter.format(document.createdAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${document.pageCount}p",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
