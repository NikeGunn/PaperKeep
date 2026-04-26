package app.paperkeep.feature.scanner.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paperkeep.core.imaging.FilterPreviewStrip
import app.paperkeep.core.imaging.ImageFilter
import app.paperkeep.core.imaging.ImageFilterProcessor
import app.paperkeep.core.imaging.Quad
import kotlin.math.roundToInt

// Test tags
const val TAG_CROP_SCREEN = "crop_screen"
const val TAG_CORNER_TL = "corner_handle_tl"
const val TAG_CORNER_TR = "corner_handle_tr"
const val TAG_CORNER_BR = "corner_handle_br"
const val TAG_CORNER_BL = "corner_handle_bl"
const val TAG_ROTATE_BUTTON = "rotate_button"
const val TAG_RETAKE_BUTTON = "retake_button"
const val TAG_NEXT_BUTTON = "next_button"
const val TAG_FILTER_STRIP = "filter_preview_strip"

private val HandleRadius = 14.dp
private val HandleColor = Color(0xFFFFB020) // Saffron brand colour

/**
 * Crop screen — shown after document capture (P1.10 + P3.1).
 *
 * Phase 3.1 additions:
 *  - [DocTypeChip] showing the detected document type with a tappable override
 *  - [FilterPreviewStrip] at the bottom for manual filter selection
 *  - Auto-applied filter from the classifier policy
 */
@Composable
fun CropScreen(
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val captureState by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_CROP_SCREEN),
    ) {
        when (val state = captureState) {
            is CaptureState.ReadyToCrop -> {
                CropContent(
                    state = state,
                    onQuadUpdated = viewModel::onQuadUpdated,
                    onRotate = viewModel::rotateImage,
                    onRetake = viewModel::retake,
                    onNext = onNext,
                    onFilterSelected = viewModel::onFilterSelected,
                    onDocTypeOverride = viewModel::onDocTypeOverride,
                )
            }
            is CaptureState.Processing,
            CaptureState.Capturing -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            else -> {
                Text(
                    text = "No image to crop",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun CropContent(
    state: CaptureState.ReadyToCrop,
    onQuadUpdated: (Quad) -> Unit,
    onRotate: () -> Unit,
    onRetake: () -> Unit,
    onNext: () -> Unit,
    onFilterSelected: (ImageFilter) -> Unit,
    onDocTypeOverride: (app.paperkeep.core.ml.DocumentType) -> Unit,
) {
    // Apply selected filter on preview so crop and filter feedback stay in sync.
    val displayBitmap = remember(state.image, state.selectedFilter) {
        ImageFilterProcessor.apply(state.image, state.selectedFilter)
    }

    var localQuad by remember(state.image) { mutableStateOf(state.quad) }
    LaunchedEffect(state.image, state.quad) {
        localQuad = state.quad
    }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val mapper = remember(displayBitmap.width, displayBitmap.height, viewportSize) {
        buildCropCoordinateMapper(
            imageWidth = displayBitmap.width,
            imageHeight = displayBitmap.height,
            viewportWidth = viewportSize.width,
            viewportHeight = viewportSize.height,
        )
    }

    val maxX = (displayBitmap.width - 1).coerceAtLeast(0).toFloat()
    val maxY = (displayBitmap.height - 1).coerceAtLeast(0).toFloat()

    fun updateQuad(update: (Quad) -> Quad) {
        val updated = update(localQuad)
        localQuad = updated
        onQuadUpdated(updated)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── DocType chip row ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocTypeChip(
                classification = state.classification,
                onTypeOverride = onDocTypeOverride,
            )
        }

        // ── Image canvas with draggable corner handles ──────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { viewportSize = it },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bmp = displayBitmap.asImageBitmap()
                val drawWidth = (displayBitmap.width * mapper.scale).roundToInt().coerceAtLeast(1)
                val drawHeight = (displayBitmap.height * mapper.scale).roundToInt().coerceAtLeast(1)

                drawImage(
                    image = bmp,
                    dstOffset = IntOffset(
                        x = mapper.offsetX.roundToInt(),
                        y = mapper.offsetY.roundToInt(),
                    ),
                    dstSize = IntSize(drawWidth, drawHeight),
                )

                val tl = mapper.imageToScreen(localQuad.topLeft)
                val tr = mapper.imageToScreen(localQuad.topRight)
                val br = mapper.imageToScreen(localQuad.bottomRight)
                val bl = mapper.imageToScreen(localQuad.bottomLeft)
                val path = Path().apply {
                    moveTo(tl.x, tl.y)
                    lineTo(tr.x, tr.y)
                    lineTo(br.x, br.y)
                    lineTo(bl.x, bl.y)
                    close()
                }
                drawPath(path, color = HandleColor.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx()))
            }

            val tl = mapper.imageToScreen(localQuad.topLeft)
            val tr = mapper.imageToScreen(localQuad.topRight)
            val br = mapper.imageToScreen(localQuad.bottomRight)
            val bl = mapper.imageToScreen(localQuad.bottomLeft)

            // Drag uses screen-space delta → image-space delta → applied to
            // the current corner. Avoids the recomposition feedback loop that
            // happens when each event recomputes from absolute position.
            CornerHandle(
                x = tl.x,
                y = tl.y,
                tag = TAG_CORNER_TL,
                contentDescription = "Top-left corner handle",
                onDragDelta = { dxScreen, dyScreen ->
                    val delta = mapper.screenDeltaToImageDelta(dxScreen, dyScreen)
                    updateQuad { quad ->
                        quad.copy(
                            topLeft = clampPoint(
                                x = quad.topLeft.x + delta.x,
                                y = quad.topLeft.y + delta.y,
                                maxX = maxX, maxY = maxY,
                            ),
                        )
                    }
                },
            )

            CornerHandle(
                x = tr.x,
                y = tr.y,
                tag = TAG_CORNER_TR,
                contentDescription = "Top-right corner handle",
                onDragDelta = { dxScreen, dyScreen ->
                    val delta = mapper.screenDeltaToImageDelta(dxScreen, dyScreen)
                    updateQuad { quad ->
                        quad.copy(
                            topRight = clampPoint(
                                x = quad.topRight.x + delta.x,
                                y = quad.topRight.y + delta.y,
                                maxX = maxX, maxY = maxY,
                            ),
                        )
                    }
                },
            )

            CornerHandle(
                x = br.x,
                y = br.y,
                tag = TAG_CORNER_BR,
                contentDescription = "Bottom-right corner handle",
                onDragDelta = { dxScreen, dyScreen ->
                    val delta = mapper.screenDeltaToImageDelta(dxScreen, dyScreen)
                    updateQuad { quad ->
                        quad.copy(
                            bottomRight = clampPoint(
                                x = quad.bottomRight.x + delta.x,
                                y = quad.bottomRight.y + delta.y,
                                maxX = maxX, maxY = maxY,
                            ),
                        )
                    }
                },
            )

            CornerHandle(
                x = bl.x,
                y = bl.y,
                tag = TAG_CORNER_BL,
                contentDescription = "Bottom-left corner handle",
                onDragDelta = { dxScreen, dyScreen ->
                    val delta = mapper.screenDeltaToImageDelta(dxScreen, dyScreen)
                    updateQuad { quad ->
                        quad.copy(
                            bottomLeft = clampPoint(
                                x = quad.bottomLeft.x + delta.x,
                                y = quad.bottomLeft.y + delta.y,
                                maxX = maxX, maxY = maxY,
                            ),
                        )
                    }
                },
            )
        }

        // ── Filter preview strip (P2.7 + P3.1 wire-up) ────────────────────────
        Spacer(modifier = Modifier.height(8.dp))
        FilterPreviewStrip(
            sourceBitmap = state.image,
            selectedFilter = state.selectedFilter,
            onFilterSelected = onFilterSelected,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_FILTER_STRIP),
        )

        // ── Bottom controls ────────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier
                    .testTag(TAG_RETAKE_BUTTON)
                    .semantics { contentDescription = "Retake photo" },
            ) {
                Text("Retake")
            }

            FilledIconButton(
                onClick = onRotate,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(TAG_ROTATE_BUTTON)
                    .semantics { contentDescription = "Rotate image 90 degrees" },
            ) {
                Icon(Icons.Filled.RotateRight, contentDescription = null)
            }

            Button(
                onClick = onNext,
                modifier = Modifier
                    .testTag(TAG_NEXT_BUTTON)
                    .semantics { contentDescription = "Confirm crop and continue" },
            ) {
                Text("Next")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * A draggable corner handle. Reports drag deltas (in screen px) per pointer
 * event — the parent converts to image-space and applies to the current
 * quad. This avoids the absolute-position closure-capture bug that caused
 * the handle to drift relative to the finger.
 *
 * The pointer-input block is keyed on a constant [Unit], so the listener is
 * installed exactly once. We capture nothing from the parent's recomposing
 * state inside the listener — only [onDragDelta], which is recreated each
 * recomposition but referenced via a stable lambda by Compose's input
 * dispatch.
 */
@Composable
private fun CornerHandle(
    x: Float,
    y: Float,
    tag: String,
    contentDescription: String,
    onDragDelta: (dxScreen: Float, dyScreen: Float) -> Unit,
) {
    val handleSize = 48.dp
    val density = LocalDensity.current
    val halfHandlePx = with(density) { handleSize.toPx() / 2f }

    // Snapshot the latest callback so the pointerInput block can see updates
    // without re-installing the listener (which would lose drag continuity).
    val latestDragDelta = androidx.compose.runtime.rememberUpdatedState(onDragDelta)

    Canvas(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (x - halfHandlePx).roundToInt(),
                    y = (y - halfHandlePx).roundToInt(),
                )
            }
            .size(handleSize)
            .testTag(tag)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    latestDragDelta.value(dragAmount.x, dragAmount.y)
                }
            },
    ) {
        drawCircle(color = HandleColor, radius = HandleRadius.toPx(), center = Offset(size.width / 2f, size.height / 2f))
        drawCircle(color = Color.White, radius = HandleRadius.toPx() * 0.4f, center = Offset(size.width / 2f, size.height / 2f))
    }
}

private fun clampPoint(x: Float, y: Float, maxX: Float, maxY: Float): app.paperkeep.core.imaging.Point2f {
    return app.paperkeep.core.imaging.Point2f(
        x = x.coerceIn(0f, maxX),
        y = y.coerceIn(0f, maxY),
    )
}
