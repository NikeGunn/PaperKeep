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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paperkeep.core.imaging.ImageFilter
import app.paperkeep.core.imaging.Point2f
import app.paperkeep.core.imaging.Quad
import app.paperkeep.core.imaging.FilterPreviewStrip

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
    var tlX by rememberSaveable { mutableStateOf(state.quad.topLeft.x) }
    var tlY by rememberSaveable { mutableStateOf(state.quad.topLeft.y) }
    var trX by rememberSaveable { mutableStateOf(state.quad.topRight.x) }
    var trY by rememberSaveable { mutableStateOf(state.quad.topRight.y) }
    var brX by rememberSaveable { mutableStateOf(state.quad.bottomRight.x) }
    var brY by rememberSaveable { mutableStateOf(state.quad.bottomRight.y) }
    var blX by rememberSaveable { mutableStateOf(state.quad.bottomLeft.x) }
    var blY by rememberSaveable { mutableStateOf(state.quad.bottomLeft.y) }

    fun currentQuad() = Quad(
        topLeft = Point2f(tlX, tlY),
        topRight = Point2f(trX, trY),
        bottomRight = Point2f(brX, brY),
        bottomLeft = Point2f(blX, blY),
    )

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
                .clipToBounds(),
        ) {
            val bitmap = state.image

            Canvas(modifier = Modifier.fillMaxSize()) {
                val bmp = bitmap.asImageBitmap()
                val scaleX = size.width / bitmap.width
                val scaleY = size.height / bitmap.height
                val scale = minOf(scaleX, scaleY)
                val offsetX = (size.width - bitmap.width * scale) / 2f
                val offsetY = (size.height - bitmap.height * scale) / 2f
                drawImage(image = bmp, topLeft = Offset(offsetX, offsetY))
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(tlX, tlY); lineTo(trX, trY); lineTo(brX, brY); lineTo(blX, blY); close()
                }
                drawPath(path, color = HandleColor.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx()))
            }

            CornerHandle(x = tlX, y = tlY, tag = TAG_CORNER_TL, contentDescription = "Top-left corner handle",
                onDrag = { dx, dy -> tlX += dx; tlY += dy; onQuadUpdated(currentQuad()) })

            CornerHandle(x = trX, y = trY, tag = TAG_CORNER_TR, contentDescription = "Top-right corner handle",
                onDrag = { dx, dy -> trX += dx; trY += dy; onQuadUpdated(currentQuad()) })

            CornerHandle(x = brX, y = brY, tag = TAG_CORNER_BR, contentDescription = "Bottom-right corner handle",
                onDrag = { dx, dy -> brX += dx; brY += dy; onQuadUpdated(currentQuad()) })

            CornerHandle(x = blX, y = blY, tag = TAG_CORNER_BL, contentDescription = "Bottom-left corner handle",
                onDrag = { dx, dy -> blX += dx; blY += dy; onQuadUpdated(currentQuad()) })
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

@Composable
private fun CornerHandle(
    x: Float,
    y: Float,
    tag: String,
    contentDescription: String,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    val handleSize = 48.dp
    Canvas(
        modifier = Modifier
            .size(handleSize)
            .testTag(tag)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
    ) {
        drawCircle(color = HandleColor, radius = HandleRadius.toPx(), center = Offset(size.width / 2f, size.height / 2f))
        drawCircle(color = Color.White, radius = HandleRadius.toPx() * 0.4f, center = Offset(size.width / 2f, size.height / 2f))
    }
}
