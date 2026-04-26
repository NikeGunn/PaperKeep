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
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paperkeep.core.data.compose.EncryptedImage

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

private const val ZOOM_PAGER_LOCK_EPSILON = 0.01f

internal fun isPagerSwipeEnabled(zoomScale: Float): Boolean {
    return zoomScale <= ReaderViewModel.MIN_ZOOM + ZOOM_PAGER_LOCK_EPSILON
}

internal fun shouldAllowPan(zoomScale: Float): Boolean {
    return !isPagerSwipeEnabled(zoomScale)
}

/**
 * Full-screen document reader.
 *
 * Spec §5 Phase 2 / §6.5:
 *  - HorizontalPager with pinch-to-zoom (scale 1..5)
 *  - Bottom action bar: share / delete / rename / reorder (→ reorder screen) / add page / export
 *  - OCR text overlay toggle — transparent selectable text at page bottom
 *  - FLAG_SECURE set immediately via SideEffect (per-Activity, survives config change)
 *  - Tap anywhere on image hides/shows top+bottom bars (immersive toggle)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    documentId: String,
    onNavigateBack: () -> Unit,
    onNavigateToReorder: (String) -> Unit = {},
    onAddPage: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
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
    val snackbarState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(documentId) { viewModel.loadDocument(documentId) }

    // Re-fetch when the screen returns to the foreground (e.g. after the
    // user added a page via the camera flow).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
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

    // Holds the last "Save to..." request so the SAF result handler knows
    // which file to copy bytes from.
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

    // Handle one-shot events
    LaunchedEffect(event) {
        when (val e = event) {
            is ReaderEvent.DocumentDeleted -> {
                viewModel.consumeEvent()
                onNavigateBack()
            }
            is ReaderEvent.ShareIntent -> {
                viewModel.consumeEvent()
                launchShareIntent(context, e.uri, e.mimeType)
            }
            is ReaderEvent.ShareMultipleIntent -> {
                viewModel.consumeEvent()
                launchShareMultipleIntent(context, e.uris, e.mimeType)
            }
            is ReaderEvent.StartSafExport -> {
                viewModel.consumeEvent()
                pendingExport = PendingExport(e.sourceFilePath)
                safExportLauncher.launch(e.suggestedName)
            }
            is ReaderEvent.Toast -> {
                viewModel.consumeEvent()
                snackbarState.showSnackbar(e.message)
            }
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
                    currentPage = currentPage,
                    isRenaming = isRenaming,
                    ocrOverlayEnabled = ocrOverlayEnabled,
                    onNavigateBack = onNavigateBack,
                    onToggleOcr = viewModel::toggleOcrOverlay,
                    onStartRename = viewModel::startRename,
                    onTitleChanged = viewModel::onTitleChanged,
                    onCommitRename = viewModel::commitRename,
                    onCancelRename = viewModel::cancelRename,
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                ReaderBottomBar(
                    documentId = documentId,
                    onShare = viewModel::openShareSheet,
                    onDelete = viewModel::deleteDocument,
                    onReorder = { onNavigateToReorder(documentId) },
                    onAddPage = onAddPage,
                    onExport = viewModel::openDownloadSheet,
                    modifier = Modifier.testTag(TAG_READER_BOTTOM_BAR),
                )
            }
        },
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(TAG_READER_SCREEN),
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No pages found", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        var currentPageScale by remember { mutableStateOf(ReaderViewModel.MIN_ZOOM) }

        val pagerState = rememberPagerState(
            initialPage = currentPage.coerceIn(0, pages.size - 1),
            pageCount = { pages.size },
        )

        LaunchedEffect(pagerState.currentPage) {
            viewModel.onPageChanged(pagerState.currentPage)
            currentPageScale = ReaderViewModel.MIN_ZOOM
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = isPagerSwipeEnabled(currentPageScale),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TAG_PAGE_PAGER),
            ) { pageIndex ->
                val page = pages[pageIndex]
                ZoomablePage(
                    imageFile = java.io.File(page.encryptedImagePath),
                    ocrText = if (ocrOverlayEnabled) page.ocrText else null,
                    onTap = viewModel::toggleBottomBar,
                    onScaleChanged = { scale ->
                        if (pagerState.currentPage == pageIndex) {
                            currentPageScale = scale
                        }
                    },
                )
            }

            // Page counter
            if (pages.size > 1) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${pages.size}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }

            if (isBusy) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.30f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
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
    }
}

// ── Pending SAF export wrapper ────────────────────────────────────────────────

private data class PendingExport(val sourceFilePath: String)

// ── Share / SAF helpers ───────────────────────────────────────────────────────

private fun launchShareIntent(
    context: android.content.Context,
    uri: Uri,
    mimeType: String,
) {
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

private fun launchShareMultipleIntent(
    context: android.content.Context,
    uris: List<Uri>,
    mimeType: String,
) {
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

// ── Format chooser bottom sheet ───────────────────────────────────────────────

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
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
                subtitle = if (hasOcrText)
                    "Recognized text (.txt)"
                else
                    "Recognized text — not ready yet",
                tag = "format_text",
                onClick = { onPick(ShareFormat.TEXT) },
            )
        }
    }
}

@Composable
private fun FormatRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag),
    )
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    pageCount: Int,
    currentPage: Int,
    isRenaming: Boolean,
    ocrOverlayEnabled: Boolean,
    onNavigateBack: () -> Unit,
    onToggleOcr: () -> Unit,
    onStartRename: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onCommitRename: () -> Unit,
    onCancelRename: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = if (isRenaming) onCancelRename else onNavigateBack,
                modifier = Modifier.semantics { contentDescription = if (isRenaming) "Cancel rename" else "Navigate back" },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .testTag(TAG_READER_TITLE)
                            .clickable(onClick = onStartRename)
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
                    modifier = Modifier
                        .testTag(TAG_READER_RENAME)
                        .semantics { contentDescription = "Save title" },
                ) {
                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null)
                }
            } else {
                IconButton(
                    onClick = onToggleOcr,
                    modifier = Modifier
                        .testTag(TAG_OCR_TOGGLE)
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
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        ),
    )
}

// ── Bottom action bar ─────────────────────────────────────────────────────────

@Composable
private fun ReaderBottomBar(
    documentId: String,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onReorder: () -> Unit,
    onAddPage: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(
                onClick = onShare,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(TAG_READER_SHARE)
                    .semantics { contentDescription = "Share page" },
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(TAG_READER_DELETE)
                    .semantics { contentDescription = "Delete document" },
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            IconButton(
                onClick = onReorder,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Reorder pages" },
            ) {
                Icon(Icons.Filled.SortByAlpha, contentDescription = null)
            }
            IconButton(
                onClick = onAddPage,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(TAG_READER_ADD_PAGE)
                    .semantics { contentDescription = "Add page to this document" },
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
            }
            IconButton(
                onClick = onExport,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(TAG_READER_EXPORT)
                    .semantics { contentDescription = "Export document" },
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null)
            }
        }
    }
}

// ── Zoomable page ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomablePage(
    imageFile: java.io.File,
    ocrText: String?,
    onTap: () -> Unit,
    onScaleChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(ReaderViewModel.MIN_ZOOM, ReaderViewModel.MAX_ZOOM)
        if (shouldAllowPan(scale)) {
            offset += panChange * scale
        } else {
            // Reset pan when at minimum zoom.
            offset = Offset.Zero
        }
    }

    LaunchedEffect(scale) {
        onScaleChanged(scale)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        EncryptedImage(
            file = imageFile.takeIf { it.exists() },
            contentDescription = "Document page",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .transformable(
                    state = transformableState,
                    canPan = { shouldAllowPan(scale) },
                )
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )

        // OCR overlay: selectable text drawn at bottom
        if (!ocrText.isNullOrBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .testTag(TAG_READER_OCR_OVERLAY)
                    .semantics { contentDescription = "OCR text overlay" },
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = ocrText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
