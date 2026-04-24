package app.paperkeep.feature.reader

import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage

/** Test tags for instrumented tests. */
const val TAG_READER_SCREEN = "reader_screen"
const val TAG_OCR_TOGGLE = "ocr_overlay_toggle"
const val TAG_PAGE_PAGER = "reader_pager"

/**
 * Full-screen document reader with pinch-to-zoom and optional OCR text overlay.
 *
 * FLAG_SECURE is set while this screen is visible to prevent screenshots.
 */
@Composable
fun ReaderScreen(
    documentId: String,
    navController: NavController,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val ocrOverlayEnabled by viewModel.ocrOverlayEnabled.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    // FLAG_SECURE: prevent screenshots/screen recording of sensitive documents
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (pages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pages found", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = currentPage,
        pageCount = { pages.size },
    )

    LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageChanged(pagerState.currentPage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_READER_SCREEN),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAG_PAGE_PAGER),
        ) { pageIndex ->
            val page = pages[pageIndex]
            ZoomablePage(
                imagePath = page.encryptedImagePath,
                ocrText = if (ocrOverlayEnabled) page.ocrText else null,
            )
        }

        // OCR text overlay toggle button
        FloatingActionButton(
            onClick = viewModel::toggleOcrOverlay,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag(TAG_OCR_TOGGLE)
                .semantics { contentDescription = "Toggle OCR text overlay" },
            containerColor = if (ocrOverlayEnabled)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(
                imageVector = Icons.Filled.TextFields,
                contentDescription = null,
                tint = if (ocrOverlayEnabled)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Page counter indicator
        if (pages.size > 1) {
            Text(
                text = "${pagerState.currentPage + 1} / ${pages.size}",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

/**
 * A single page view with pinch-to-zoom support (scale clamped to 1..5).
 * When [ocrText] is non-null it is shown as a semi-transparent overlay.
 */
@Composable
private fun ZoomablePage(
    imagePath: String,
    ocrText: String?,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(ReaderViewModel.MIN_ZOOM, ReaderViewModel.MAX_ZOOM)
        offset += panChange
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imagePath,
            contentDescription = "Document page",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformableState)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )

        // OCR text overlay — displayed when the toggle is enabled
        if (!ocrText.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                Text(
                    text = ocrText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier
                        .graphicsLayer { alpha = 0.85f },
                )
            }
        }
    }
}
