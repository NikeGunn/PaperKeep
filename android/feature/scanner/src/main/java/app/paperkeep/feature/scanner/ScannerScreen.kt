package app.paperkeep.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.paperkeep.feature.scanner.capture.CaptureViewModel
import app.paperkeep.feature.scanner.mlkit.rememberDocumentScannerLauncher
import app.paperkeep.feature.scanner.permission.CameraPermissionGate

const val TAG_SCANNER_SCREEN = "scanner_screen"

/**
 * Scanner entry point — there is intentionally no in-app camera preview.
 *
 * The instant this composable enters the composition we launch Google's
 * document scanner (ML Kit DocumentScanner) which provides the full capture
 * UX (viewfinder, edge detection, auto-shutter, manual corner adjustment,
 * multi-page batch, perspective correction, colour filters). When the user
 * finishes scanning, the cropped page bitmaps flow back through
 * [CaptureViewModel.savePreCroppedPage] which encrypts, classifies, OCRs,
 * and writes them to the document store — exactly like the legacy flow,
 * minus the redundant in-app crop screen.
 *
 * If the user cancels the scanner, we pop straight back to wherever they
 * came from (no awkward intermediate state). On error we do the same and
 * surface a brief error message via [onError].
 *
 * UX rationale (FAANG-grade):
 *  - One tap to scan: button → Google scanner → done. No double-handoff.
 *  - The user never sees a stale Paperkeep camera preview "behind" the
 *    Google scanner. We show a minimal "Opening scanner…" splash while
 *    Play Services warms up.
 *  - The composable is full-screen edge-to-edge so the system status bar
 *    blends with the dark splash background instead of clashing with the
 *    camera preview underneath.
 */
@Composable
fun ScannerScreen(
    onPagesReady: () -> Unit = {},
    onCancelled: () -> Unit = {},
    onError: (Throwable) -> Unit = {},
    modifier: Modifier = Modifier,
    captureViewModel: CaptureViewModel = hiltViewModel(),
) {
    // ML Kit batches multiple pages into one result. We collect them all
    // before handing the whole batch to the filter-review screen so the user
    // picks one filter for the entire scan session.
    val scannedPages = remember { mutableStateListOf<android.graphics.Bitmap>() }

    val launcher = rememberDocumentScannerLauncher(
        onPageScanned = { bitmap -> scannedPages.add(bitmap) },
        onCancelled = {
            if (scannedPages.isEmpty()) {
                onCancelled()
            } else {
                // ML Kit can deliver pages before signalling cancellation if
                // the user backed out mid-batch. Honour the pages they did scan.
                captureViewModel.beginFilterReview(scannedPages.toList())
                scannedPages.clear()
                onPagesReady()
            }
        },
        onError = onError,
        onSuccess = {
            if (scannedPages.isNotEmpty()) {
                captureViewModel.beginFilterReview(scannedPages.toList())
                scannedPages.clear()
                onPagesReady()
            } else {
                onCancelled()
            }
        },
    )

    // CAMERA permission isn't strictly required by ML Kit (it owns its own
    // camera lifecycle), but the system scanner still respects it. Asking
    // here keeps the on-first-launch UX consistent.
    CameraPermissionGate {
        LaunchedEffect(Unit) { launcher.launch() }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag(TAG_SCANNER_SCREEN),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = "Opening scanner…",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
