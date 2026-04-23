package app.paperkeep.core.imaging

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Horizontal scrollable strip showing a thumbnail preview for each [ImageFilter] (2B.6).
 *
 * [sourceBitmap] is downsampled before filters are applied — thumbnails are
 * rendered at ~120 px wide, so full-resolution processing is avoided.
 *
 * @param sourceBitmap The perspective-corrected page image.
 * @param selectedFilter Currently selected filter.
 * @param onFilterSelected Callback when a filter thumbnail is tapped.
 */
@Composable
fun FilterPreviewStrip(
    sourceBitmap: Bitmap,
    selectedFilter: ImageFilter,
    onFilterSelected: (ImageFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Scale source down to thumbnail size once, then apply each filter on the small bitmap.
    val thumbBitmap = remember(sourceBitmap) {
        val targetW = 120
        val targetH = (targetW * sourceBitmap.height.toFloat() / sourceBitmap.width.toFloat()).toInt()
            .coerceAtLeast(1)
        Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true)
    }

    val filteredBitmaps = remember(thumbBitmap) {
        ImageFilter.entries.associateWith { filter ->
            ImageFilterProcessor.apply(thumbBitmap, filter)
        }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(ImageFilter.entries, key = { it.key }) { filter ->
            FilterThumbnail(
                bitmap = filteredBitmaps[filter] ?: thumbBitmap,
                filter = filter,
                isSelected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun FilterThumbnail(
    bitmap: Bitmap,
    filter: ImageFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val shape = RoundedCornerShape(8.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${filter.label} filter${if (isSelected) ", selected" else ""}"
                role = Role.Button
            },
    ) {
        Box(
            modifier = Modifier
                .size(64.dp, 80.dp)
                .clip(shape)
                .border(borderWidth, borderColor, shape),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape),
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                )
            }
        }

        Text(
            text = filter.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
