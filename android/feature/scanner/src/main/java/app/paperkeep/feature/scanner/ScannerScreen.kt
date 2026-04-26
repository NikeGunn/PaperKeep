package app.paperkeep.feature.scanner

import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.paperkeep.feature.scanner.camera.CameraControlsOverlay
import app.paperkeep.feature.scanner.camera.CameraPreviewView
import app.paperkeep.feature.scanner.camera.takePictureBitmap
import app.paperkeep.feature.scanner.capture.CaptureViewModel
import app.paperkeep.feature.scanner.permission.CameraPermissionGate
import app.paperkeep.feature.scanner.recent.RecentScansThumbnailStrip

const val TAG_SCANNER_SCREEN = "scanner_screen"

/**
 * Top-level camera / scanner screen.
 *
 * Layers (bottom → top):
 *  1. CameraPreviewView  — live camera preview + ImageCapture use-case
 *  2. CameraControlsOverlay — torch, grid, zoom, focus, capture button
 *  3. RecentScansThumbnailStrip — last 10 scans, pinned above capture button
 *
 * When the shutter fires, [ImageCapture.takePictureBitmap] decodes the
 * full-res JPEG into a Bitmap and hands it to [CaptureViewModel.onImageCaptured],
 * which runs edge detection + perspective warp on the default dispatcher and
 * then navigates to the crop screen via [onCaptureDone].
 */
@Composable
fun ScannerScreen(
    onCaptureDone: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    modifier: Modifier = Modifier,
    captureViewModel: CaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    CameraPermissionGate {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag(TAG_SCANNER_SCREEN),
        ) {
            CameraPreviewView(
                onImageCaptureReady = { imageCapture = it },
            )

            CameraControlsOverlay(
                onCapture = {
                    imageCapture?.takePictureBitmap(
                        executor = ContextCompat.getMainExecutor(context),
                        onBitmap = { bitmap ->
                            captureViewModel.onImageCaptured(bitmap)
                            onCaptureDone()
                        },
                    )
                },
            )

            RecentScansThumbnailStrip(
                onOpenLibrary = onOpenLibrary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
            )
        }
    }
}
