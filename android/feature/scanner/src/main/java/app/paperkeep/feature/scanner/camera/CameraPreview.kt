package app.paperkeep.feature.scanner.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

const val TAG_CAMERA_PREVIEW = "camera_preview_view"

/**
 * A Compose-compatible wrapper around [PreviewView] that binds both a
 * [Preview] and an [ImageCapture] use-case.
 *
 * Call [onImageCaptureReady] to receive the [ImageCapture] instance, then
 * call [ImageCapture.takePicture] when the user presses the shutter.
 */
@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    onImageCaptureReady: (ImageCapture) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType          = PreviewView.ScaleType.FILL_CENTER
                tag                = TAG_CAMERA_PREVIEW

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()
                        .also { it.surfaceProvider = this.surfaceProvider }

                    val selector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()

                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)

                    onImageCaptureReady(imageCapture)
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(TAG_CAMERA_PREVIEW),
    )
}

/**
 * Take a picture and deliver the decoded [Bitmap] on the main thread.
 * Silently drops on error — the camera stays live.
 */
fun ImageCapture.takePictureBitmap(
    executor: java.util.concurrent.Executor,
    onBitmap: (Bitmap) -> Unit,
) {
    takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val bitmap = try {
                image.toBitmap()
            } catch (_: Exception) {
                null
            } finally {
                image.close()
            }
            if (bitmap != null) {
                onBitmap(bitmap)
            }
        }
        override fun onError(exc: ImageCaptureException) {
            // Silently drop — no crash. The shutter button stays live.
        }
    })
}

private fun ImageProxy.toBitmap(): Bitmap {
    val nv21 = toNv21()
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val jpeg = ByteArrayOutputStream().use { stream ->
        yuv.compressToJpeg(Rect(0, 0, width, height), 100, stream)
        stream.toByteArray()
    }

    val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        ?: throw IllegalStateException("Unable to decode captured image")

    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return decoded

    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}

private fun ImageProxy.toNv21(): ByteArray {
    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val ySize = yPlane.buffer.remaining()
    val uBytes = ByteArray(uPlane.buffer.remaining()).also { uPlane.buffer.get(it) }
    val vBytes = ByteArray(vPlane.buffer.remaining()).also { vPlane.buffer.get(it) }
    val nv21 = ByteArray(ySize + uBytes.size + vBytes.size)

    yPlane.buffer.get(nv21, 0, ySize)

    var outputOffset = ySize
    val chromaHeight = height / 2
    val chromaWidth = width / 2

    for (row in 0 until chromaHeight) {
        val uRowOffset = row * uPlane.rowStride
        val vRowOffset = row * vPlane.rowStride
        for (col in 0 until chromaWidth) {
            val uIndex = uRowOffset + col * uPlane.pixelStride
            val vIndex = vRowOffset + col * vPlane.pixelStride
            if (uIndex >= uBytes.size || vIndex >= vBytes.size) continue
            nv21[outputOffset++] = vBytes[vIndex]
            nv21[outputOffset++] = uBytes[uIndex]
        }
    }

    return nv21
}
