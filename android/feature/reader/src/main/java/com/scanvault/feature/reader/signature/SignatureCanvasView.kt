package com.scanvault.feature.reader.signature

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import java.io.ByteArrayOutputStream

/** Represents a single drawn stroke as a list of [Offset] points. */
data class SignatureStroke(val points: List<Offset>)

/**
 * Composable canvas that lets the user draw their signature (3B.6).
 *
 * Tracks finger/stylus paths. Call [SignatureState.toPngBytes] to export the
 * result as a PNG byte array suitable for [SignatureRepository.save].
 */
@Composable
fun SignatureCanvasView(
    state: SignatureState,
    modifier: Modifier = Modifier,
) {
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Canvas(
        modifier = modifier
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentStroke = listOf(offset)
                    },
                    onDrag = { change, _ ->
                        currentStroke = currentStroke + change.position
                    },
                    onDragEnd = {
                        if (currentStroke.isNotEmpty()) {
                            state.addStroke(SignatureStroke(currentStroke))
                        }
                        currentStroke = emptyList()
                    },
                    onDragCancel = { currentStroke = emptyList() },
                )
            },
    ) {
        val strokeStyle = Stroke(
            width = 4f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        // Draw committed strokes
        for (stroke in state.strokes) {
            if (stroke.points.size < 2) continue
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(stroke.points.first().x, stroke.points.first().y)
            for (pt in stroke.points.drop(1)) path.lineTo(pt.x, pt.y)
            drawPath(path, Color.Black, style = strokeStyle)
        }

        // Draw in-progress stroke
        if (currentStroke.size >= 2) {
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(currentStroke.first().x, currentStroke.first().y)
            for (pt in currentStroke.drop(1)) path.lineTo(pt.x, pt.y)
            drawPath(path, Color.Black, style = strokeStyle)
        }
    }
}

/**
 * Holds the state for a [SignatureCanvasView].
 *
 * Create via `remember { SignatureState() }` in a Composable, or inject
 * a pre-populated instance for testing.
 */
class SignatureState {

    private val _strokes = mutableStateListOf<SignatureStroke>()
    val strokes: List<SignatureStroke> get() = _strokes

    internal fun addStroke(stroke: SignatureStroke) {
        _strokes.add(stroke)
    }

    /** Clear all strokes. */
    fun clear() = _strokes.clear()

    /** @return `true` if the user has drawn at least one stroke. */
    fun isEmpty(): Boolean = _strokes.isEmpty()

    /**
     * Rasterise all strokes to a 400×200 ARGB_8888 bitmap and encode as PNG.
     *
     * Returns `null` if no strokes have been drawn.
     */
    fun toPngBytes(width: Int = 400, height: Int = 200): ByteArray? {
        if (_strokes.isEmpty()) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 4f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (stroke in _strokes) {
            if (stroke.points.size < 2) continue
            val path = android.graphics.Path()
            path.moveTo(stroke.points.first().x, stroke.points.first().y)
            for (pt in stroke.points.drop(1)) path.lineTo(pt.x, pt.y)
            canvas.drawPath(path, paint)
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
