package app.paperkeep.feature.scanner.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import app.paperkeep.core.ml.ClassificationResult
import app.paperkeep.core.ml.DocTypePolicies
import app.paperkeep.core.ml.DocumentType

const val TAG_DOC_TYPE_CHIP = "doc_type_chip"
const val TAG_DOC_TYPE_PICKER = "doc_type_picker"

/**
 * P3.1 — Document-type chip shown on the crop screen.
 *
 * States:
 *  - [classification] = null → "Detecting…" animated indicator
 *  - [classification] != null → chip shows type + emoji + confidence
 *
 * Tapping the chip opens a bottom-sheet-style picker where the user can
 * override the detected type. On override, [onTypeOverride] is called and the
 * recommended filter + aspect are auto-applied by [CaptureViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocTypeChip(
    classification: ClassificationResult?,
    onTypeOverride: (DocumentType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        DocTypePickerDialog(
            current = classification?.type,
            onSelect = { type ->
                onTypeOverride(type)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    if (classification == null) {
        DetectingIndicator(modifier = modifier)
    } else {
        val policy = DocTypePolicies.forType(classification.type)
        FilterChip(
            selected = true,
            onClick = { showPicker = true },
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(classification.type.emoji)
                    Text(
                        text = classification.type.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = modifier
                .testTag(TAG_DOC_TYPE_CHIP)
                .semantics {
                    contentDescription = "Document type: ${classification.type.label}. Tap to change."
                },
        )
    }
}

@Composable
private fun DetectingIndicator(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .width(80.dp)
                .height(4.dp),
        )
        Text(
            text = "Detecting…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DocTypePickerDialog(
    current: DocumentType?,
    onSelect: (DocumentType) -> Unit,
    onDismiss: () -> Unit,
) {
    val types = listOf(
        DocumentType.DOCUMENT,
        DocumentType.RECEIPT,
        DocumentType.ID_CARD,
        DocumentType.WHITEBOARD,
        DocumentType.BOOK_PAGE,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Document type") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_DOC_TYPE_PICKER),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Choose the type to apply the best filter automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                types.forEach { type ->
                    val policy = DocTypePolicies.forType(type)
                    FilterChip(
                        selected = type == current,
                        onClick = { onSelect(type) },
                        label = {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(type.emoji)
                                    Text(type.label, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    text = policy.recommendationLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "${type.label}: ${policy.recommendationLabel}" },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
