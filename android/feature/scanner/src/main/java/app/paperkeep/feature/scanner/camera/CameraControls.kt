package app.paperkeep.feature.scanner.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Test tags
const val TAG_TORCH_BUTTON = "torch_toggle_button"
const val TAG_GRID_BUTTON = "grid_toggle_button"
const val TAG_CAPTURE_BUTTON = "capture_button"
const val TAG_GRID_OVERLAY = "grid_overlay"
const val TAG_FOCUS_RING = "focus_ring"

/** Minimum touch target size per Material 3 / WCAG 2.5.5 — 48dp. */
val MIN_TOUCH_TARGET = 48.dp

/**
 * Overlay composable that renders camera controls:
 *  - Top bar: torch toggle, grid toggle
 *  - Middle: gesture layer (pinch-to-zoom, tap-to-focus)
 *  - Grid: optional 3×3 rule-of-thirds lines
 *  - Focus ring: spring-animated AF indicator at tap point
 *  - Bottom: capture button with haptic feedback
 */
@Composable
fun CameraControlsOverlay(
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraControlsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    Box(modifier = modifier.fillMaxSize()) {

        // ── Gesture layer (pinch-to-zoom + tap-to-focus) ─────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        viewModel.onZoomChanged(state.zoomRatio * zoom)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        viewModel.onTapToFocus(offset.x, offset.y)
                    }
                }
        )

        // ── 3×3 grid overlay ─────────────────────────────────────────────────
        if (state.isGridVisible) {
            GridOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TAG_GRID_OVERLAY),
            )
        }

        // ── Focus ring ───────────────────────────────────────────────────────
        state.focusPoint?.let { fp ->
            FocusRing(
                x = fp.x,
                y = fp.y,
                onAnimationEnd = { viewModel.clearFocusPoint() },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TAG_FOCUS_RING),
            )
        }

        // ── Top control bar ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            // Torch toggle
            FilledIconToggleButton(
                checked = state.isTorchOn,
                onCheckedChange = { viewModel.toggleTorch() },
                modifier = Modifier
                    .size(MIN_TOUCH_TARGET)
                    .testTag(TAG_TORCH_BUTTON)
                    .semantics { contentDescription = "Toggle torch" },
                colors = IconButtonDefaults.filledIconToggleButtonColors(),
            ) {
                Icon(
                    imageVector = if (state.isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = null,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            // Grid toggle
            FilledIconToggleButton(
                checked = state.isGridVisible,
                onCheckedChange = { viewModel.toggleGrid() },
                modifier = Modifier
                    .size(MIN_TOUCH_TARGET)
                    .testTag(TAG_GRID_BUTTON)
                    .semantics { contentDescription = "Toggle grid overlay" },
                colors = IconButtonDefaults.filledIconToggleButtonColors(),
            ) {
                Icon(
                    imageVector = if (state.isGridVisible) Icons.Filled.GridOn else Icons.Filled.GridOff,
                    contentDescription = null,
                )
            }
        }

        // ── Capture button ───────────────────────────────────────────────────
        FilledIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCapture()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(72.dp)
                .testTag(TAG_CAPTURE_BUTTON)
                .semantics { contentDescription = "Capture document" },
        ) {
            // White shutter circle inside amber filled button
            Canvas(modifier = Modifier.size(56.dp)) {
                drawCircle(color = Color.White)
            }
        }
    }
}

/**
 * Draws a 3×3 rule-of-thirds grid — 2 vertical + 2 horizontal lines.
 */
@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    val lineColor = Color.White.copy(alpha = 0.4f)
    Canvas(modifier = modifier) {
        val thirdW = size.width / 3f
        val thirdH = size.height / 3f
        val stroke = Stroke(width = 1.dp.toPx())

        // 2 vertical lines
        drawLine(lineColor, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = stroke.width)
        drawLine(lineColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = stroke.width)

        // 2 horizontal lines
        drawLine(lineColor, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = stroke.width)
        drawLine(lineColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), strokeWidth = stroke.width)
    }
}

/**
 * Animated AF focus ring — appears at [x],[y], springs in, then calls [onAnimationEnd].
 */
@Composable
fun FocusRing(
    x: Float,
    y: Float,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1.4f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(x, y) {
        // Spring in from large to normal size, then fade out
        scale.snapTo(1.4f)
        alpha.snapTo(1f)
        scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f))
        alpha.animateTo(0f)
        onAnimationEnd()
    }

    val ringColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        drawCircle(
            color = ringColor.copy(alpha = alpha.value),
            radius = 32.dp.toPx() * scale.value,
            center = Offset(x, y),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
