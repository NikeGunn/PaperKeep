package app.paperkeep.feature.scanner.edge

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.paperkeep.core.imaging.Point2f
import app.paperkeep.core.imaging.Quad

const val TAG_EDGE_OVERLAY_GOOD = "edge_overlay_good"
const val TAG_EDGE_OVERLAY_WEAK = "edge_overlay_weak"
const val TAG_EDGE_OVERLAY_SETTLING = "edge_overlay_settling"
const val TAG_EDGE_OVERLAY_LOCKED = "edge_overlay_locked"
const val TAG_EDGE_OVERLAY_PARTIAL = "edge_overlay_partial"
const val TAG_EDGE_OVERLAY_NONE = "edge_overlay_none"

private val GreenStrong = Color(0xFF22C55E)   // confident detection
private val YellowWeak  = Color(0xFFFACC15)   // tentative detection
private val AmberOverlay = Color(0xFFFFB020)
private val SaffronOverlay = Color(0xFFF59E0B) // brand anchor — locked colour
private val RedNoDetect = Color(0xFFEF4444)

private const val STRONG_THRESHOLD = 0.55f

/**
 * Real-time edge detection overlay. Renders the detected quad, a stability
 * progress arc per corner, and the lock-confirmation flash.
 *
 * Colour language (matches CamScanner):
 *  - GREEN  → confident detection (confidence ≥ STRONG_THRESHOLD)
 *  - YELLOW → weak detection (quad found but low confidence — still tracking)
 *  - SAFFRON → locked / auto-capture firing
 *  - AMBER dashed border → partial (contours but no clean quad)
 *  - invisible → nothing detected
 *
 * Corner positions are smoothly interpolated between frames using a
 * per-frame lerp so the quad does not snap as the detector updates.
 */
@Composable
fun EdgeDetectionOverlay(
    state: EdgeOverlayState,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = when (state) {
            is EdgeOverlayState.Good -> 1f
            is EdgeOverlayState.Weak -> 1f
            is EdgeOverlayState.Settling -> 1f
            is EdgeOverlayState.Locked -> 1f
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
        is EdgeOverlayState.Weak -> TAG_EDGE_OVERLAY_WEAK
        is EdgeOverlayState.Settling -> TAG_EDGE_OVERLAY_SETTLING
        is EdgeOverlayState.Locked -> TAG_EDGE_OVERLAY_LOCKED
        EdgeOverlayState.Partial -> TAG_EDGE_OVERLAY_PARTIAL
        EdgeOverlayState.None -> TAG_EDGE_OVERLAY_NONE
    }

    // Smooth the rendered quad toward the target each frame. Without this the
    // detector's frame-to-frame updates would visibly snap the corners.
    val target = quadFor(state)
    var current by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target == null) {
            current = null
            return@LaunchedEffect
        }
        if (current == null) {
            current = target
            return@LaunchedEffect
        }
        // 8 frames of ease-in (~130ms @ 60Hz) is enough to feel smooth without lagging.
        repeat(8) {
            withFrameNanos { }
            current = lerpQuad(current ?: target, target, 0.35f)
        }
        current = target
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag(tag),
    ) {
        if (alpha < 0.01f) return@Canvas
        when (state) {
            is EdgeOverlayState.Good -> {
                val q = current ?: state.quad
                drawQuad(q, GreenStrong.copy(alpha = alpha), fillTint = true)
                drawConfidenceDot(q, state.confidence, GreenStrong.copy(alpha = alpha))
            }
            is EdgeOverlayState.Weak -> {
                val q = current ?: state.quad
                drawQuad(q, YellowWeak.copy(alpha = alpha), fillTint = false)
                drawConfidenceDot(q, state.confidence, YellowWeak.copy(alpha = alpha))
            }
            is EdgeOverlayState.Settling -> {
                val q = current ?: state.quad
                val color = if (state.confidence >= STRONG_THRESHOLD) GreenStrong else YellowWeak
                drawQuad(q, color.copy(alpha = alpha), fillTint = state.confidence >= STRONG_THRESHOLD)
                drawProgress(q, state.progress.coerceIn(0f, 1f), SaffronOverlay.copy(alpha = alpha))
            }
            is EdgeOverlayState.Locked -> {
                val q = current ?: state.quad
                drawQuad(q, SaffronOverlay.copy(alpha = alpha), fillTint = true, strokeMultiplier = 1.6f)
            }
            EdgeOverlayState.Partial -> drawRect(
                color = AmberOverlay.copy(alpha = alpha),
                style = Stroke(width = 3.dp.toPx()),
            )
            EdgeOverlayState.None -> { /* invisible */ }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawQuad(
    quad: Quad,
    color: Color,
    fillTint: Boolean = false,
    strokeMultiplier: Float = 1f,
) {
    val path = Path().apply {
        moveTo(quad.topLeft.x, quad.topLeft.y)
        lineTo(quad.topRight.x, quad.topRight.y)
        lineTo(quad.bottomRight.x, quad.bottomRight.y)
        lineTo(quad.bottomLeft.x, quad.bottomLeft.y)
        close()
    }
    if (fillTint) {
        // Semi-transparent fill inside the document — gives users the
        // "this is what will be cropped" cue without obscuring the preview.
        drawPath(path, color = color.copy(alpha = color.alpha * 0.15f), style = Fill)
    }
    drawPath(path, color = color, style = Stroke(width = 2.dp.toPx() * strokeMultiplier))

    val len = 24.dp.toPx()
    val strokeWidth = 3.dp.toPx() * strokeMultiplier
    val handleRadius = 6.dp.toPx() * strokeMultiplier
    val corners = listOf(
        quad.topLeft to Pair(Offset(quad.topLeft.x + len, quad.topLeft.y), Offset(quad.topLeft.x, quad.topLeft.y + len)),
        quad.topRight to Pair(Offset(quad.topRight.x - len, quad.topRight.y), Offset(quad.topRight.x, quad.topRight.y + len)),
        quad.bottomRight to Pair(Offset(quad.bottomRight.x - len, quad.bottomRight.y), Offset(quad.bottomRight.x, quad.bottomRight.y - len)),
        quad.bottomLeft to Pair(Offset(quad.bottomLeft.x + len, quad.bottomLeft.y), Offset(quad.bottomLeft.x, quad.bottomLeft.y - len)),
    )
    corners.forEach { (corner, lines) ->
        drawLine(color, Offset(corner.x, corner.y), lines.first, strokeWidth = strokeWidth)
        drawLine(color, Offset(corner.x, corner.y), lines.second, strokeWidth = strokeWidth)
        // Filled corner handle — gives the corners a tangible "anchor" feel.
        drawCircle(color = color, radius = handleRadius, center = Offset(corner.x, corner.y))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawProgress(
    quad: Quad,
    progress: Float,
    color: Color,
) {
    val radius = 8.dp.toPx()
    val sweep = 360f * progress
    val strokeWidth = 3.dp.toPx()
    listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft).forEach { p ->
        val topLeft = Offset(p.x - radius, p.y - radius)
        val size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = size,
            style = Stroke(width = strokeWidth),
        )
    }
}

/**
 * A small confidence arc near the top-left corner — visually tells the user
 * how sure the detector is about this quad. Fuller arc = stronger detection.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConfidenceDot(
    quad: Quad,
    confidence: Float,
    color: Color,
) {
    val radius = 6.dp.toPx()
    val sweep = 360f * confidence.coerceIn(0f, 1f)
    val anchor = quad.topLeft
    val topLeft = Offset(anchor.x - radius - 14.dp.toPx(), anchor.y - radius - 14.dp.toPx())
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = 2.dp.toPx()),
    )
}

private fun quadFor(state: EdgeOverlayState): Quad? = when (state) {
    is EdgeOverlayState.Good -> state.quad
    is EdgeOverlayState.Weak -> state.quad
    is EdgeOverlayState.Settling -> state.quad
    is EdgeOverlayState.Locked -> state.quad
    EdgeOverlayState.Partial -> null
    EdgeOverlayState.None -> null
}

private fun lerpQuad(a: Quad, b: Quad, t: Float): Quad = Quad(
    topLeft = lerpPt(a.topLeft, b.topLeft, t),
    topRight = lerpPt(a.topRight, b.topRight, t),
    bottomRight = lerpPt(a.bottomRight, b.bottomRight, t),
    bottomLeft = lerpPt(a.bottomLeft, b.bottomLeft, t),
)

private fun lerpPt(a: Point2f, b: Point2f, t: Float): Point2f =
    Point2f(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
