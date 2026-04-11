package com.scanvault.feature.scanner.camera

import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

const val TAG_CAMERA_PREVIEW = "camera_preview_view"

/**
 * A Compose-compatible wrapper around [PreviewView] with:
 *  - 4:3 aspect ratio (correct for document scanning)
 *  - Safe-area insets respected via [Modifier.windowInsetsPadding]
 *  - Back-camera lens facing
 *
 * The [PreviewView] is an AndroidView because CameraX's preview surface
 * requires a SurfaceView/TextureView — not directly renderable in Compose Canvas.
 */
@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    onPreviewViewCreated: (PreviewView) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                tag = TAG_CAMERA_PREVIEW

                // Bind CameraX when the view is attached to the window
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
                    provider.bindToLifecycle(lifecycleOwner, selector, preview)

                    onPreviewViewCreated(this)
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(TAG_CAMERA_PREVIEW),
    )
}
