package app.paperkeep.feature.reader.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.paperkeep.core.data.compose.EncryptedImage
import java.io.File
import kotlin.math.abs

const val ZOOMABLE_PAGE_TAG_PREFIX = "zoomable_page_"
const val TAG_PAGE_OCR_OVERLAY_PREFIX = "page_ocr_overlay_"

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Renders a single document page at its natural aspect ratio inside a
 * vertical-scroll viewer. Supports:
 *
 *  - Pinch zoom (1× → 5×) per page, independent state per page
 *  - Double-tap toggle: 1× ↔ 2.5×
 *  - Single tap → [onSingleTap] (used to toggle bars)
 *  - When zoomed in, pan offsets are clamped so the image cannot leave the
 *    viewport. This makes landscape pages pannable inside their slot.
 *
 * The composable measures its own slot to width and infers height from
 * [aspectRatio] = `width / height`. Mixed-orientation documents thus get
 * per-page natural sizing without forcing every page to the same dimensions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomablePageItem(
    pageIndex: Int,
    imageFile: File?,
    aspectRatio: Float,
    ocrText: String?,
    pageTitle: String?,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeAspect = if (aspectRatio.isFinite() && aspectRatio > 0f) aspectRatio else 1f
    var scale by remember { mutableFloatStateOf(MIN_ZOOM) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var slotSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    val animatedScale by animateFloatAsState(targetValue = scale, label = "page_zoom")
    val touchSlop = LocalViewConfiguration.current.touchSlop

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(safeAspect, matchHeightConstraintsFirst = false)
            .padding(vertical = 4.dp)
            .testTag(ZOOMABLE_PAGE_TAG_PREFIX + pageIndex)
            .semantics { contentDescription = pageTitle ?: "Page ${pageIndex + 1}" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onSingleTap() },
                        onDoubleTap = {
                            if (abs(scale - MIN_ZOOM) < 0.01f) {
                                scale = DOUBLE_TAP_ZOOM
                            } else {
                                scale = MIN_ZOOM
                                offset = Offset.Zero
                            }
                        },
                    )
                }
                // Custom gesture handler: zoom + pan **only when the user
                // actually intends to zoom or pan a zoomed page**. At scale=1
                // single-finger drags are left for the parent LazyColumn so
                // the document scrolls between pages. At scale>1 single-finger
                // drags pan the image — but if the pan would push past the
                // edge, the LazyColumn still consumes the residual movement
                // so the user can scroll the document without first releasing
                // and re-touching. Two-finger pinches always belong to us.
                //
                // This is the same gating Photos / Drive Scan use; replaces
                // Compose's stock detectTransformGestures which greedily
                // consumes every single-finger drag and breaks parent scroll.
                .pointerInput(safeAspect) {
                    awaitEachGesture {
                        // Wait for the first finger down (don't consume it).
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var totalZoom = 1f
                        var totalPanX = 0f
                        var totalPanY = 0f
                        var claimed = false
                        do {
                            val event = awaitPointerEvent(pass = PointerEventPass.Main)
                            val pointerCount = event.changes.count { it.pressed }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            totalZoom *= zoomChange
                            totalPanX += panChange.x
                            totalPanY += panChange.y

                            val anyMoved = event.changes.any { it.positionChanged() }
                            if (!anyMoved) continue

                            // Decide once per gesture whether to claim it.
                            if (!claimed) {
                                val pinchedEnough = pointerCount >= 2 &&
                                    abs(totalZoom - 1f) > 0.02f
                                val panAtZoom = scale > MIN_ZOOM + 0.01f &&
                                    (abs(totalPanX) > touchSlop || abs(totalPanY) > touchSlop)
                                if (pinchedEnough || panAtZoom) {
                                    claimed = true
                                }
                            }

                            if (!claimed) continue

                            // Apply zoom around centroid so pinch feels anchored.
                            val newScale = (scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            scale = newScale
                            if (newScale <= MIN_ZOOM + 0.01f) {
                                offset = Offset.Zero
                            } else {
                                offset = clampOffsetToBounds(
                                    offset = offset + panChange,
                                    slot = slotSize,
                                    scale = newScale,
                                )
                            }
                            // Consume so the LazyColumn doesn't also scroll.
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer(
                    scaleX = animatedScale,
                    scaleY = animatedScale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        ) {
            EncryptedImage(
                file = imageFile?.takeIf { it.exists() },
                contentDescription = pageTitle ?: "Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .matchParentSize()
                    .onSizeChanged { size ->
                        slotSize = androidx.compose.ui.geometry.Size(
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                    },
            )
        }

        if (!pageTitle.isNullOrBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        if (!ocrText.isNullOrBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .testTag(TAG_PAGE_OCR_OVERLAY_PREFIX + pageIndex),
            ) {
                SelectionContainer {
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

/**
 * Clamp a pan offset so the zoomed image never leaves the slot bounds.
 * Pure function so it's JVM-unit-testable.
 *
 * At scale=1 the offset must be zero. At scale=s>1 the image renders
 * s×slot wide/tall around the slot center, so the visible image can be
 * translated by ±(s-1)/2 × slotDim on each axis.
 */
internal fun clampOffsetToBounds(
    offset: Offset,
    slot: androidx.compose.ui.geometry.Size,
    scale: Float,
): Offset {
    if (slot.width <= 0f || slot.height <= 0f || scale <= 1f) return Offset.Zero
    val maxX = (scale - 1f) * slot.width / 2f
    val maxY = (scale - 1f) * slot.height / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

