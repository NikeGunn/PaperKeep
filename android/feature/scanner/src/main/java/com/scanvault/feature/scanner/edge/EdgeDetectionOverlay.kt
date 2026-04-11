package com.scanvault.feature.scanner.edge

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.scanvault.core.imaging.Quad

const val TAG_EDGE_OVERLAY_GOOD = "edge_overlay_good"
const val TAG_EDGE_OVERLAY_PARTIAL = "edge_overlay_partial"
const val TAG_EDGE_OVERLAY_NONE = "edge_overlay_none"

private val GreenOverlay = Color(0xFF4CAF50)
private val AmberOverlay = Color(0xFFFFB020)

/**
 * Composable overlay for 1B.12 — drawn on top of the camera preview.
 *
 * States:
 *  - [EdgeOverlayState.Good]    → green quad outline with corner accents
 *  - [EdgeOverlayState.Partial] → amber dashed indicator
 *  - [EdgeOverlayState.None]    → invisible (alpha = 0)
 *
 * Transitions use a spring animation so the overlay snaps with physics rather
 * than linear fades, matching the "80ms spring" from the spec.
 */
@Composable
fun EdgeDetectionOverlay(
    state: EdgeOverlayState,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = when (state) {
            is EdgeOverlayState.Good -> 1f
            EdgeOverlayState.Partial -> 0.8f
            EdgeOverlayState.None -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "overlay_alpha",
    )

    val tag = when (state) {
        is EdgeOverlayState.Good -> TAG_EDGE_OVERLAY_GOOD
        EdgeOverlayState.Partial -> TAG_EDGE_OVERLAY_PARTIAL
        EdgeOverlayState.None -> TAG_EDGE_OVERLAY_NONE
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag(tag),
    ) {
        if (alpha < 0.01f) return@Canvas

        when (state) {
            is EdgeOverlayState.Good -> {
                drawQuad(state.quad, GreenOverlay.copy(alpha = alpha))
            }
            EdgeOverlayState.Partial -> {
                // Amber border around the whole canvas as a partial indicator
                drawRect(
                    color = AmberOverlay.copy(alpha = alpha),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
            EdgeOverlayState.None -> { /* invisible */ }
        }
    }
}

/** Draws the quad outline and corner accents in [color]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawQuad(
    quad: Quad,
    color: Color,
) {
    val path = Path().apply {
        moveTo(quad.topLeft.x, quad.topLeft.y)
        lineTo(quad.topRight.x, quad.topRight.y)
        lineTo(quad.bottomRight.x, quad.bottomRight.y)
        lineTo(quad.bottomLeft.x, quad.bottomLeft.y)
        close()
    }
    drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))

    // Corner accent lines (L-shaped brackets)
    val len = 24.dp.toPx()
    val stroke = Stroke(width = 3.dp.toPx())
    listOf(
        quad.topLeft to Pair(Offset(quad.topLeft.x + len, quad.topLeft.y), Offset(quad.topLeft.x, quad.topLeft.y + len)),
        quad.topRight to Pair(Offset(quad.topRight.x - len, quad.topRight.y), Offset(quad.topRight.x, quad.topRight.y + len)),
        quad.bottomRight to Pair(Offset(quad.bottomRight.x - len, quad.bottomRight.y), Offset(quad.bottomRight.x, quad.bottomRight.y - len)),
        quad.bottomLeft to Pair(Offset(quad.bottomLeft.x + len, quad.bottomLeft.y), Offset(quad.bottomLeft.x, quad.bottomLeft.y - len)),
    ).forEach { (corner, lines) ->
        drawLine(color, Offset(corner.x, corner.y), lines.first, strokeWidth = stroke.width)
        drawLine(color, Offset(corner.x, corner.y), lines.second, strokeWidth = stroke.width)
    }
}
