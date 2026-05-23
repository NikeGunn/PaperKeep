package app.paperkeep.feature.scanner.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.util.Size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

const val TAG_CAMERA_PREVIEW = "camera_preview_view"

/**
 * A Compose-compatible wrapper around [PreviewView] that binds both a
 * [Preview] and an [ImageCapture] use-case.
 *
 * Call [onImageCaptureReady] to receive the [ImageCapture] instance, then
 * call [ImageCapture.takePicture] when the user presses the shutter.
 */
/**
 * Tap event delivered to a [TapHandler]. Carries both the view-space tap
 * coordinates (for triggering camera focus/metering) and a mapping function
 * from view-space → analysis-bitmap-space.
 *
 * Coordinate mapping is non-trivial because [PreviewView]'s
 * `FILL_CENTER` scale type crops the camera buffer on whichever axis
 * doesn't match the view's aspect ratio. The analysis bitmap is the
 * already-rotated camera buffer; mapping a view-space tap into it requires
 * inverting that FILL_CENTER transform, NOT a naive linear scale.
 *
 * Wrong mapping was the source of the "I tapped the notebook but the
 * detector lit up a corner cable" bug — the tap landed on a totally
 * different region of the analysis bitmap than the user pointed at.
 */
data class PreviewTap(
    val viewX: Float,
    val viewY: Float,
    val viewWidth: Float,
    val viewHeight: Float,
    val latestBitmap: Bitmap?,
) {
    /**
     * Map the view-space tap to bitmap-pixel coordinates, inverting the
     * `FILL_CENTER` scale type. Returns null if the tap fell in a region
     * that's outside the visible camera buffer (cropped away by FILL_CENTER).
     */
    fun toBitmapXY(): Pair<Float, Float>? {
        val bmp = latestBitmap ?: return null
        if (viewWidth <= 0f || viewHeight <= 0f || bmp.width <= 0 || bmp.height <= 0) return null

        // FILL_CENTER: the bitmap is scaled uniformly to fully cover the view
        // (max scale on both axes), then centered. Some part of the bitmap
        // is cropped off the side whose aspect is smaller.
        val scale = maxOf(
            viewWidth / bmp.width.toFloat(),
            viewHeight / bmp.height.toFloat(),
        )
        val scaledBmpW = bmp.width * scale
        val scaledBmpH = bmp.height * scale
        val offsetX = (viewWidth - scaledBmpW) / 2f   // negative when bitmap wider than view
        val offsetY = (viewHeight - scaledBmpH) / 2f  // negative when bitmap taller than view

        val bx = (viewX - offsetX) / scale
        val by = (viewY - offsetY) / scale

        if (bx < 0f || bx >= bmp.width || by < 0f || by >= bmp.height) return null
        return bx to by
    }
}

typealias TapHandler = (PreviewTap) -> Unit

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    onImageCaptureReady: (ImageCapture) -> Unit = {},
    onAnalysisFrame: ((Bitmap) -> Unit)? = null,
    onTap: TapHandler? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // Hold a reference to the bound Camera so the tap handler can drive
    // FocusMeteringAction on the same camera instance.
    val cameraRef = remember { mutableStateOf<Camera?>(null) }
    // Cache the latest analysis bitmap so taps can map view-space → bitmap-space.
    val latestBitmapRef = remember { mutableStateOf<Bitmap?>(null) }
    // Dimensions of the preview view in pixels — populated by the AndroidView.
    val previewSizeRef = remember { mutableStateOf(0 to 0) }
    // Hold the PreviewView so the tap handler can use its meteringPointFactory.
    val previewViewRef = remember { mutableStateOf<PreviewView?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            // Better still quality improves downstream edge/quad detection fidelity.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    // Live edge-detection feed. The analyzer runs at most one frame at a time
    // (STRATEGY_KEEP_ONLY_LATEST); back-pressure naturally throttles detection
    // to whatever rate Magic Scan can handle on the device.
    val imageAnalysis = remember(onAnalysisFrame) {
        if (onAnalysisFrame == null) return@remember null
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        )
                    )
                    .build()
            )
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
                previewViewRef.value = this

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

                    imageAnalysis?.setAnalyzer(
                        ContextCompat.getMainExecutor(ctx),
                    ) { proxy ->
                        try {
                            val bitmap = proxy.toAnalysisBitmap()
                            if (bitmap != null) {
                                // Always cache the latest frame so tap-to-detect
                                // has something fresh to work with. The optional
                                // onAnalysisFrame is only invoked when a caller
                                // wants per-frame detection (auto-Magic-Scan).
                                latestBitmapRef.value = bitmap
                                onAnalysisFrame?.invoke(bitmap)
                            }
                        } catch (_: Throwable) {
                            // Drop frame; back-pressure will catch us up.
                        } finally {
                            proxy.close()
                        }
                    }

                    provider.unbindAll()
                    val useCases = buildList {
                        add(preview)
                        add(imageCapture)
                        if (imageAnalysis != null) add(imageAnalysis)
                    }.toTypedArray()
                    cameraRef.value = provider.bindToLifecycle(lifecycleOwner, selector, *useCases)

                    onImageCaptureReady(imageCapture)
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(TAG_CAMERA_PREVIEW)
            .pointerInput(onTap) {
                if (onTap == null) return@pointerInput
                previewSizeRef.value = size.width to size.height
                detectTapGestures(
                    onTap = { offset ->
                        val (vw, vh) = previewSizeRef.value.let { (w, h) ->
                            (if (w > 0) w else size.width).toFloat() to
                                (if (h > 0) h else size.height).toFloat()
                        }
                        // Trigger CameraX focus/metering at the tap point. We
                        // use PreviewView.meteringPointFactory (NOT a
                        // SurfaceOrientedMeteringPointFactory built from view
                        // size) — the former accounts for rotation, mirroring,
                        // and the FILL_CENTER crop. The previous implementation
                        // metered the wrong sensor region on portrait phones.
                        val cam = cameraRef.value
                        val pv = previewViewRef.value
                        if (cam != null && pv != null) {
                            val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction
                                .Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            try { cam.cameraControl.startFocusAndMetering(action) } catch (_: Throwable) { }
                        }
                        onTap(
                            PreviewTap(
                                viewX = offset.x,
                                viewY = offset.y,
                                viewWidth = vw,
                                viewHeight = vh,
                                latestBitmap = latestBitmapRef.value,
                            )
                        )
                    },
                )
            },
    )
}

/**
 * Decode an [ImageProxy] for the live edge-detection pipeline. The bitmap is
 * the downsampled analysis frame so we keep allocations small (~640×480).
 */
private fun ImageProxy.toAnalysisBitmap(): Bitmap? {
    return try {
        val nv21 = toNv21()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val jpeg = ByteArrayOutputStream().use { stream ->
            yuv.compressToJpeg(Rect(0, 0, width, height), 80, stream)
            stream.toByteArray()
        }
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) decoded else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
    } catch (_: Throwable) {
        null
    }
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
