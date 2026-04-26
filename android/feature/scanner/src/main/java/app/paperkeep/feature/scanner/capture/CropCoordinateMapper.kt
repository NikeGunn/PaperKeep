package app.paperkeep.feature.scanner.capture

import androidx.compose.ui.geometry.Offset
import app.paperkeep.core.imaging.Point2f

internal data class CropCoordinateMapper(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    fun imageToScreen(point: Point2f): Offset {
        return Offset(
            x = offsetX + (point.x * scale),
            y = offsetY + (point.y * scale),
        )
    }

    fun screenDeltaToImageDelta(dx: Float, dy: Float): Point2f {
        val safeScale = if (scale <= 0f) 1f else scale
        return Point2f(x = dx / safeScale, y = dy / safeScale)
    }
    fun screenToImage(
        x: Float,
        y: Float,
        imageWidth: Float,
        imageHeight: Float,
    ): Point2f {
        val safeScale = if (scale <= 0f) 1f else scale
        val imageX = ((x - offsetX) / safeScale).coerceIn(0f, imageWidth.coerceAtLeast(0f))
        val imageY = ((y - offsetY) / safeScale).coerceIn(0f, imageHeight.coerceAtLeast(0f))
        return Point2f(imageX, imageY)
    }

    fun applyDrag(
        point: Point2f,
        dx: Float,
        dy: Float,
        imageWidth: Float,
        imageHeight: Float,
    ): Point2f {
        val delta = screenDeltaToImageDelta(dx, dy)
        return Point2f(
            x = (point.x + delta.x).coerceIn(0f, imageWidth.coerceAtLeast(0f)),
            y = (point.y + delta.y).coerceIn(0f, imageHeight.coerceAtLeast(0f)),
        )
    }
}

internal fun buildCropCoordinateMapper(
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
): CropCoordinateMapper {
    if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
        return CropCoordinateMapper(scale = 1f, offsetX = 0f, offsetY = 0f)
    }

    val sx = viewportWidth.toFloat() / imageWidth.toFloat()
    val sy = viewportHeight.toFloat() / imageHeight.toFloat()
    val scale = minOf(sx, sy).coerceAtLeast(0.0001f)

    val drawWidth = imageWidth * scale
    val drawHeight = imageHeight * scale

    return CropCoordinateMapper(
        scale = scale,
        offsetX = (viewportWidth - drawWidth) / 2f,
        offsetY = (viewportHeight - drawHeight) / 2f,
    )
}
