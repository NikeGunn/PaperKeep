package app.paperkeep.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.paperkeep.core.data.compose.EncryptedImage
import app.paperkeep.core.domain.model.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val cardShape = RoundedCornerShape(16.dp)
private val dateFormatter = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .withZone(ZoneId.systemDefault())

/**
 * A document card for the library grid.
 *
 * Displays: thumbnail, title (1 line), page count, relative date, "on device" pill,
 * optional favourite icon, optional colour tag dot, selection overlay.
 *
 * Per spec §5 Phase 2 / §7 trust signals: every card shows the "on device" pill
 * as a constant trust signal — no cloud icon, no sync status (there is no backend).
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
            .testTag("document_card_${document.id}")
            .semantics {
                contentDescription = buildString {
                    append("Document: ${document.title}")
                    append(", ${document.pageCount} page${if (document.pageCount != 1) "s" else ""}")
                    if (document.isFavorite) append(", favourited")
                    if (document.isArchived) append(", archived")
                    document.docType?.let { append(", type: $it") }
                    append(", on device")
                }
            },
    ) {
        Column {
            // ── Thumbnail area ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val thumbFile = document.pages.firstOrNull()
                    ?.encryptedThumbPath
                    ?.let { java.io.File(it) }
                    ?.takeIf { it.exists() }

                val imageFileFallback = document.pages.firstOrNull()
                    ?.encryptedImagePath
                    ?.let { java.io.File(it) }
                    ?.takeIf { it.exists() }

                EncryptedImage(
                    file = thumbFile ?: imageFileFallback,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )

                // Selection overlay + checkmark
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

                // Favourite star — top-start when not in selection mode
                if (document.isFavorite && !isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Favourited",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(16.dp),
                    )
                }

                // Colour tag dot — bottom-start
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

                // "On device" trust pill — bottom-end (replaces cloud/sync icon)
                OnDevicePill(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }

            // ── Title + metadata ───────────────────────────────────────────────
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dateFormatter.format(document.createdAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "·",
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

/**
 * Compact "on device" pill — a trust signal shown on every document card.
 * Communicates to privacy-conscious users that nothing left the device.
 */
@Composable
fun OnDevicePill(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.semantics { contentDescription = "Stored on device" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(8.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "on device",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
