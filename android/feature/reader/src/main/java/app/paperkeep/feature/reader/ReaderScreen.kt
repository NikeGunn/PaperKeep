package app.paperkeep.feature.reader

import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paperkeep.core.imaging.ImageFilter
import app.paperkeep.feature.reader.edit.EditTool
import app.paperkeep.feature.reader.edit.EditToolbar
import app.paperkeep.feature.reader.edit.ReaderEditViewModel
import app.paperkeep.feature.reader.viewer.DocumentViewer

/** Test tags */
const val TAG_READER_SCREEN = "reader_screen"
const val TAG_OCR_TOGGLE = "ocr_overlay_toggle"
const val TAG_PAGE_PAGER = "reader_pager"
const val TAG_READER_SHARE = "reader_share"
const val TAG_READER_DELETE = "reader_delete"
const val TAG_READER_RENAME = "reader_rename"
const val TAG_READER_EXPORT = "reader_export"
const val TAG_READER_ADD_PAGE = "reader_add_page"
const val TAG_READER_BOTTOM_BAR = "reader_bottom_bar"
const val TAG_READER_TITLE = "reader_title"
const val TAG_READER_OCR_OVERLAY = "reader_ocr_overlay"
const val TAG_READER_EDIT_TOGGLE = "reader_edit_toggle"
const val TAG_READER_UNDO = "reader_undo"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    documentId: String,
    onNavigateBack: () -> Unit,
    onNavigateToReorder: (String) -> Unit = {},
    onAddPage: () -> Unit = {},
    onRetakePage: (documentId: String, pageId: String) -> Unit = { _, _ -> },
    onReCropPage: (documentId: String, pageId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
    editViewModel: ReaderEditViewModel = hiltViewModel(),
) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val ocrOverlayEnabled by viewModel.ocrOverlayEnabled.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showBottomBar by viewModel.showBottomBar.collectAsStateWithLifecycle()
    val isRenaming by viewModel.isRenaming.collectAsStateWithLifecycle()
    val documentTitle by viewModel.documentTitle.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val formatSheetMode by viewModel.formatSheetMode.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val editMode by editViewModel.editMode.collectAsStateWithLifecycle()
    val editBusy by editViewModel.isBusy.collectAsStateWithLifecycle()
    val undoAvailable by editViewModel.undoAvailable.collectAsStateWithLifecycle()
    val editEvent by editViewModel.event.collectAsStateWithLifecycle()

    val snackbarState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(documentId) { viewModel.loadDocument(documentId) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // FLAG_SECURE — prevents screenshots of document content (§6.5)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    // Dialog state for the edit sub-tools
    var titleDialog by remember { mutableStateOf<TitleDialogState?>(null) }
    var filterDialog by remember { mutableStateOf<FilterDialogState?>(null) }
    var comingSoonTool by remember { mutableStateOf<EditTool?>(null) }

    // Edit event router
    LaunchedEffect(editEvent) {
        when (val e = editEvent) {
            is ReaderEditViewModel.EditEvent.OpenReorder -> {
                editViewModel.consumeEvent(); onNavigateToReorder(e.documentId)
            }
            is ReaderEditViewModel.EditEvent.OpenCrop -> {
                editViewModel.consumeEvent(); onReCropPage(e.documentId, e.pageId)
            }
            is ReaderEditViewModel.EditEvent.OpenRetake -> {
                editViewModel.consumeEvent(); onRetakePage(e.documentId, e.pageId)
            }
            is ReaderEditViewModel.EditEvent.PromptPageTitle -> {
                titleDialog = TitleDialogState(e.pageId, e.current.orEmpty())
                editViewModel.consumeEvent()
            }
            is ReaderEditViewModel.EditEvent.PromptFilter -> {
                filterDialog = FilterDialogState(e.pageId, e.current)
                editViewModel.consumeEvent()
            }
            is ReaderEditViewModel.EditEvent.ComingSoon -> {
                comingSoonTool = e.tool; editViewModel.consumeEvent()
            }
            is ReaderEditViewModel.EditEvent.Toast -> {
                editViewModel.consumeEvent(); snackbarState.showSnackbar(e.message)
            }
            null -> Unit
        }
    }

    // Share/export event router (unchanged)
    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }
    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { destUri: Uri? ->
        val request = pendingExport
        pendingExport = null
        if (destUri != null && request != null) {
            viewModel.writeExportTo(
                destUri = destUri,
                sourceFilePath = request.sourceFilePath,
            ) { uri -> context.contentResolver.openOutputStream(uri) }
        }
    }
    LaunchedEffect(event) {
        when (val e = event) {
            is ReaderEvent.DocumentDeleted -> { viewModel.consumeEvent(); onNavigateBack() }
            is ReaderEvent.ShareIntent -> {
                viewModel.consumeEvent(); launchShareIntent(context, e.uri, e.mimeType)
            }
            is ReaderEvent.ShareMultipleIntent -> {
                viewModel.consumeEvent(); launchShareMultipleIntent(context, e.uris, e.mimeType)
            }
            is ReaderEvent.StartSafExport -> {
                viewModel.consumeEvent()
                pendingExport = PendingExport(e.sourceFilePath)
                safExportLauncher.launch(e.suggestedName)
            }
            is ReaderEvent.Toast -> { viewModel.consumeEvent(); snackbarState.showSnackbar(e.message) }
            null -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            ) {
                ReaderTopBar(
                    title = documentTitle,
                    pageCount = pages.size,
                    isRenaming = isRenaming,
                    editMode = editMode,
                    ocrOverlayEnabled = ocrOverlayEnabled,
                    undoAvailable = undoAvailable,
                    onNavigateBack = if (editMode) {
                        { editViewModel.exitEditMode() }
                    } else onNavigateBack,
                    onToggleOcr = viewModel::toggleOcrOverlay,
                    onStartRename = viewModel::startRename,
                    onTitleChanged = viewModel::onTitleChanged,
                    onCommitRename = viewModel::commitRename,
                    onCancelRename = viewModel::cancelRename,
                    onToggleEdit = editViewModel::toggleEditMode,
                    onUndo = editViewModel::undo,
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                if (editMode) {
                    EditToolbar(
                        onToolPicked = { tool ->
                            val page = pages.getOrNull(currentPage)
                            editViewModel.onToolPicked(
                                tool = tool,
                                documentId = documentId,
                                currentPageId = page?.id,
                                currentFilter = ImageFilter.fromKey(page?.filter ?: ImageFilter.ORIGINAL.key),
                                currentTitle = page?.title,
                            )
                        },
                    )
                } else {
                    ReaderBottomBar(
                        onShare = viewModel::openShareSheet,
                        onDelete = viewModel::deleteDocument,
                        onAddPage = onAddPage,
                        onExport = viewModel::openDownloadSheet,
                        onOpenEdit = editViewModel::toggleEditMode,
                        modifier = Modifier.testTag(TAG_READER_BOTTOM_BAR),
                    )
                }
            }
        },
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(TAG_READER_SCREEN),
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (pages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text("No pages found", style = MaterialTheme.typography.bodyLarge) }
            return@Scaffold
        }

        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            DocumentViewer(
                pages = pages,
                ocrOverlayEnabled = ocrOverlayEnabled,
                onSingleTap = viewModel::toggleBottomBar,
                onCurrentPageChanged = viewModel::onPageChanged,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.testTag(TAG_PAGE_PAGER),
            )

            if (pages.size > 1) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                ) {
                    Text(
                        text = "${currentPage + 1} / ${pages.size}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }

            if (isBusy || editBusy) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(enabled = false) {} // swallow taps while busy
                        .testTag("reader_busy_overlay"),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }

        formatSheetMode?.let { mode ->
            FormatChooserSheet(
                mode = mode,
                pageCount = pages.size,
                hasOcrText = pages.any { !it.ocrText.isNullOrBlank() },
                onDismiss = viewModel::dismissFormatSheet,
                onPick = viewModel::onFormatPicked,
            )
        }

        titleDialog?.let { state ->
            PageTitleDialog(
                initial = state.initialTitle,
                onConfirm = { newTitle ->
                    editViewModel.setPageTitle(
                        pageId = state.pageId,
                        previous = state.initialTitle.takeIf { it.isNotBlank() },
                        next = newTitle.ifBlank { null },
                    )
                    titleDialog = null
                },
                onDismiss = { titleDialog = null },
            )
        }

        filterDialog?.let { state ->
            FilterChooserSheet(
                current = state.current,
                onPick = { picked ->
                    val page = pages.firstOrNull { it.id == state.pageId }
                    if (page != null) {
                        editViewModel.applyFilter(
                            pageId = state.pageId,
                            encryptedImagePath = page.encryptedImagePath,
                            previousFilterKey = page.filter,
                            newFilter = picked,
                        )
                    }
                    filterDialog = null
                },
                onDismiss = { filterDialog = null },
            )
        }

        comingSoonTool?.let { tool ->
            ComingSoonSheet(tool = tool, onDismiss = { comingSoonTool = null })
        }
    }
}

// ── Dialog state holders ─────────────────────────────────────────────────────

private data class TitleDialogState(val pageId: String, val initialTitle: String)
private data class FilterDialogState(val pageId: String, val current: ImageFilter)
private data class PendingExport(val sourceFilePath: String)

// ── Share/SAF helpers (unchanged) ───────────────────────────────────────────

private fun launchShareIntent(context: android.content.Context, uri: Uri, mimeType: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, "Share with").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

private fun launchShareMultipleIntent(context: android.content.Context, uris: List<Uri>, mimeType: String) {
    val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mimeType
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, "Share with").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

// ── Format chooser bottom sheet (unchanged) ─────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatChooserSheet(
    mode: ReaderViewModel.FormatSheetMode,
    pageCount: Int,
    hasOcrText: Boolean,
    onDismiss: () -> Unit,
    onPick: (ShareFormat) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val verb = when (mode) {
        ReaderViewModel.FormatSheetMode.SHARE -> "Share as"
        ReaderViewModel.FormatSheetMode.DOWNLOAD -> "Save as"
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = verb,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
            )
            FormatRow(
                icon = Icons.Filled.PictureAsPdf,
                title = "PDF",
                subtitle = if (pageCount > 1) "All $pageCount pages, single file" else "1 page",
                tag = "format_pdf",
                onClick = { onPick(ShareFormat.PDF) },
            )
            FormatRow(
                icon = Icons.Filled.Image,
                title = "Image",
                subtitle = when {
                    mode == ReaderViewModel.FormatSheetMode.DOWNLOAD ->
                        "Current page as JPEG"
                    pageCount > 1 -> "All $pageCount pages as JPEGs"
                    else -> "Current page as JPEG"
                },
                tag = "format_image",
                onClick = { onPick(ShareFormat.IMAGE) },
            )
            FormatRow(
                icon = Icons.Filled.TextSnippet,
                title = "Text",
                subtitle = if (hasOcrText) "Recognized text (.txt)" else "Recognized text — not ready yet",
                tag = "format_text",
                onClick = { onPick(ShareFormat.TEXT) },
            )
        }
    }
}

@Composable
private fun FormatRow(icon: ImageVector, title: String, subtitle: String, tag: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.clickable(onClick = onClick).testTag(tag),
    )
}

// ── Page title dialog ───────────────────────────────────────────────────────

@Composable
private fun PageTitleDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page title") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(80) },
                singleLine = true,
                placeholder = { Text("e.g. Cover, Signature page") },
                modifier = Modifier.testTag("page_title_field"),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, modifier = Modifier.testTag("page_title_save")) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Filter chooser sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChooserSheet(
    current: ImageFilter,
    onPick: (ImageFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = "Filter",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
            )
            ImageFilter.values().forEach { f ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(f) }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .testTag("filter_${f.key}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = f == current, onClick = { onPick(f) })
                    Text(text = f.label, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

// ── Coming soon sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComingSoonSheet(tool: EditTool, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth().testTag("coming_soon_sheet")) {
            Text(tool.label, style = MaterialTheme.typography.titleMedium)
            Text(
                "This tool is coming soon. The 5 most-used edit actions ship in this release; the rest are queued for the next update.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(top = 16.dp)) {
                Text("Got it")
            }
        }
    }
}

// ── Top bar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    pageCount: Int,
    isRenaming: Boolean,
    editMode: Boolean,
    ocrOverlayEnabled: Boolean,
    undoAvailable: Boolean,
    onNavigateBack: () -> Unit,
    onToggleOcr: () -> Unit,
    onStartRename: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onCommitRename: () -> Unit,
    onCancelRename: () -> Unit,
    onToggleEdit: () -> Unit,
    onUndo: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = if (isRenaming) onCancelRename else onNavigateBack,
                modifier = Modifier.semantics {
                    contentDescription = when {
                        isRenaming -> "Cancel rename"
                        editMode -> "Exit edit mode"
                        else -> "Navigate back"
                    }
                },
            ) {
                Icon(
                    if (editMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
        },
        title = {
            if (isRenaming) {
                BasicTextField(
                    value = title,
                    onValueChange = onTitleChanged,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_READER_TITLE)
                        .semantics { contentDescription = "Document title edit field" },
                )
            } else {
                Column {
                    Text(
                        text = if (editMode) "Edit · $title" else title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .testTag(TAG_READER_TITLE)
                            .clickable(onClick = onStartRename, enabled = !editMode)
                            .semantics { contentDescription = "Document title: $title" },
                    )
                    if (pageCount > 0) {
                        Text(
                            text = "$pageCount page${if (pageCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        actions = {
            if (isRenaming) {
                IconButton(
                    onClick = onCommitRename,
                    modifier = Modifier.testTag(TAG_READER_RENAME)
                        .semantics { contentDescription = "Save title" },
                ) {
                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null)
                }
            } else if (editMode) {
                IconButton(
                    onClick = onUndo,
                    enabled = undoAvailable,
                    modifier = Modifier.testTag(TAG_READER_UNDO)
                        .semantics { contentDescription = "Undo last edit" },
                ) {
                    Icon(Icons.Filled.Undo, contentDescription = null)
                }
            } else {
                IconButton(
                    onClick = onToggleOcr,
                    modifier = Modifier.testTag(TAG_OCR_TOGGLE)
                        .semantics { contentDescription = "Toggle OCR text overlay" },
                ) {
                    Icon(
                        Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = if (ocrOverlayEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onToggleEdit,
                    modifier = Modifier.testTag(TAG_READER_EDIT_TOGGLE)
                        .semantics { contentDescription = "Edit mode" },
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
    )
}

// ── Bottom action bar (non-edit mode) ───────────────────────────────────────

@Composable
private fun ReaderBottomBar(
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onAddPage: () -> Unit,
    onExport: () -> Unit,
    onOpenEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.BottomAppBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(
                onClick = onAddPage,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(TAG_READER_ADD_PAGE)
                    .semantics { contentDescription = "Add page" },
            ) { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null) }
            IconButton(
                onClick = onOpenEdit,
                modifier = Modifier.size(48.dp).testTag("reader_open_edit")
                    .semantics { contentDescription = "Open edit toolbar" },
            ) { Icon(Icons.Filled.Edit, contentDescription = null) }
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(48.dp).testTag(TAG_READER_SHARE)
                    .semantics { contentDescription = "Share" },
            ) { Icon(Icons.Filled.Share, contentDescription = null) }
            IconButton(
                onClick = onExport,
                modifier = Modifier.size(48.dp).testTag(TAG_READER_EXPORT)
                    .semantics { contentDescription = "Save / Export" },
            ) { Icon(Icons.Filled.FileDownload, contentDescription = null) }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp).testTag(TAG_READER_DELETE)
                    .semantics { contentDescription = "Delete document" },
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

