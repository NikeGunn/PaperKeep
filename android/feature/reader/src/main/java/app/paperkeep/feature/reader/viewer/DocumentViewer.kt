package app.paperkeep.feature.reader.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.paperkeep.core.domain.model.Page
import java.io.File

const val TAG_DOCUMENT_VIEWER = "document_viewer_list"

/**
 * Vertical-scroll document viewer. Each page renders at its **natural**
 * aspect ratio (Feature 2): portrait pages fill the viewport width;
 * landscape pages are sized to their own width:height ratio and can be
 * pinch-zoomed + horizontally panned. No page is ever clipped on its
 * primary axis.
 *
 * The viewer exposes the currently-most-visible page back to the screen via
 * [onCurrentPageChanged] so the existing page-counter / current-page state
 * keeps working without code changes.
 */
@Composable
fun DocumentViewer(
    pages: List<Page>,
    ocrOverlayEnabled: Boolean,
    onSingleTap: () -> Unit,
    onCurrentPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
) {
    val currentPage by remember(pages, listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf 0
            // Most-visible item = the one whose center is closest to the
            // viewport center.
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            visible.minBy { kotlin.math.abs((it.offset + it.size / 2) - viewportCenter) }.index
                .coerceIn(0, pages.size - 1)
        }
    }

    LaunchedEffect(currentPage) { onCurrentPageChanged(currentPage) }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding,
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_DOCUMENT_VIEWER),
    ) {
        itemsIndexed(
            items = pages,
            key = { _, page -> page.id },
        ) { index, page ->
            val aspect = aspectRatioFor(page)
            ZoomablePageItem(
                pageIndex = index,
                imageFile = File(page.encryptedImagePath),
                aspectRatio = aspect,
                ocrText = if (ocrOverlayEnabled) page.ocrText else null,
                pageTitle = page.title,
                onSingleTap = onSingleTap,
                modifier = Modifier.padding(horizontal = 0.dp),
            )
        }
    }
}

/**
 * Returns `width / height` for a [Page], guarding against legacy rows whose
 * dimensions were not persisted at capture time. Pure function, exposed for
 * unit testing.
 */
internal fun aspectRatioFor(page: Page): Float {
    val w = page.width
    val h = page.height
    return if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 1f
}
