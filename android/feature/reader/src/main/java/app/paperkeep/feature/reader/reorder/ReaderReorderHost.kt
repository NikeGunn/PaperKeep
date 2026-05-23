package app.paperkeep.feature.reader.reorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paperkeep.core.data.compose.EncryptedImage
import java.io.File

const val TAG_REORDER_SCREEN = "reorder_screen"
const val TAG_REORDER_SAVE = "reorder_save"
const val TAG_REORDER_ITEM_PREFIX = "reorder_item_"
const val TAG_REORDER_MOVE_UP_PREFIX = "reorder_up_"
const val TAG_REORDER_MOVE_DOWN_PREFIX = "reorder_down_"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderReorderHost(
    documentId: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderReorderViewModel = hiltViewModel(),
) {
    LaunchedEffect(documentId) { viewModel.load(documentId) }

    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val busy by viewModel.isBusy.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onDone,
                        modifier = Modifier.semantics { contentDescription = "Cancel reorder" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = { Text("Reorder pages") },
                actions = {
                    IconButton(
                        onClick = { viewModel.save(onDone) },
                        modifier = Modifier
                            .testTag(TAG_REORDER_SAVE)
                            .semantics { contentDescription = "Save new order" },
                    ) {
                        Text(
                            "Save",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        modifier = modifier.testTag(TAG_REORDER_SCREEN),
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TAG_REORDER_ITEM_PREFIX + index),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(72.dp, 96.dp),
                            ) {
                                EncryptedImage(
                                    file = File(page.encryptedThumbPath)
                                        .takeIf { it.exists() }
                                        ?: File(page.encryptedImagePath).takeIf { it.exists() },
                                    contentDescription = "Page ${index + 1} thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Text(
                                text = page.title?.takeIf { it.isNotBlank() }
                                    ?: "Page ${index + 1}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            )
                            IconButton(
                                onClick = { viewModel.moveTo(index, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier
                                    .testTag(TAG_REORDER_MOVE_UP_PREFIX + index)
                                    .semantics { contentDescription = "Move page ${index + 1} up" },
                            ) { Icon(Icons.Filled.ArrowUpward, contentDescription = null) }
                            IconButton(
                                onClick = { viewModel.moveTo(index, index + 1) },
                                enabled = index < pages.size - 1,
                                modifier = Modifier
                                    .testTag(TAG_REORDER_MOVE_DOWN_PREFIX + index)
                                    .semantics { contentDescription = "Move page ${index + 1} down" },
                            ) { Icon(Icons.Filled.ArrowDownward, contentDescription = null) }
                        }
                    }
                }
            }
            if (busy) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }
}
