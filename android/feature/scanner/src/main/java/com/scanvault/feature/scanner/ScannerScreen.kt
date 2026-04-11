package com.scanvault.feature.scanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.scanvault.feature.scanner.camera.CameraControlsOverlay
import com.scanvault.feature.scanner.camera.CameraPreviewView
import com.scanvault.feature.scanner.permission.CameraPermissionGate
import com.scanvault.feature.scanner.recent.RecentScansThumbnailStrip

const val TAG_SCANNER_SCREEN = "scanner_screen"

/**
 * Top-level camera / scanner screen (1B.9–1B.17 wired together).
 *
 * Layers (bottom → top):
 *  1. CameraPreviewView — live camera preview
 *  2. CameraControlsOverlay — torch, grid, zoom, focus, capture button
 *  3. RecentScansThumbnailStrip — last 10 scans, pinned to bottom
 *
 * The entire screen is wrapped in [CameraPermissionGate] which shows
 * the rationale / denied screen when permission is not granted.
 */
@Composable
fun ScannerScreen(
    onCaptureDone: (imagePath: String) -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CameraPermissionGate {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag(TAG_SCANNER_SCREEN),
        ) {
            // Layer 1: Camera preview
            CameraPreviewView()

            // Layer 2: Controls (capture, torch, grid, zoom, focus)
            CameraControlsOverlay(
                onCapture = {
                    // In Phase 1 the captured path is a placeholder;
                    // ImageCapture callback wiring happens in 1B.13.
                    onCaptureDone("/tmp/capture_placeholder.jpg")
                },
            )

            // Layer 3: Recent scans strip at the bottom, above the capture button
            RecentScansThumbnailStrip(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp), // above the 72dp capture button + 32dp padding
            )
        }
    }
}
