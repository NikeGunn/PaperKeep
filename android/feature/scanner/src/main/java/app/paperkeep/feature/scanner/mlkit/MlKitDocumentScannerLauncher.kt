package app.paperkeep.feature.scanner.mlkit

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Integration with Google's official document scanner — the same engine that
 * powers Google Drive's "Scan" button.
 *
 * Why not our own OpenCV pipeline?
 *  - Google trains its detector on millions of real-world documents; edge
 *    detection robustness eclipses anything we can hand-tune.
 *  - Google handles the full UX: viewfinder hints, auto-shutter, manual
 *    corner adjustment, multi-page batch, perspective correction.
 *  - The model lives in Play Services and downloads on first use — zero
 *    APK bloat.
 *
 * What this file provides:
 *  - [rememberDocumentScannerLauncher] — a Compose helper that returns a
 *    callable `launch()` function. Call it (e.g. from a shutter button) and
 *    Google's scanner opens. Cropped page bitmaps are delivered via
 *    [onPageScanned] one-by-one so the existing capture pipeline (encrypt,
 *    save, classify, OCR) can process each page through its current entry
 *    point unchanged.
 */
@Composable
fun rememberDocumentScannerLauncher(
    onPageScanned: (Bitmap) -> Unit,
    onError: (Throwable) -> Unit = {},
    onCancelled: () -> Unit = {},
    // Fires exactly once after every page in a successful batch has been
    // delivered via [onPageScanned]. Use this when you need to act on the
    // batch as a whole (e.g. opening a filter-review screen) instead of
    // per-page side effects.
    onSuccess: () -> Unit = {},
): DocumentScannerLauncher {
    val context = LocalContext.current

    val options = remember {
        GmsDocumentScannerOptions.Builder()
            // No gallery import — keep flow focused on camera capture.
            .setGalleryImportAllowed(false)
            // Generous page limit; user controls when they're done.
            .setPageLimit(20)
            // We only need JPEGs — our pipeline encrypts and re-encodes anyway.
            .setResultFormats(RESULT_FORMAT_JPEG)
            // FULL gives the polished UI with auto-shutter + manual-crop +
            // colour filters. BASE is leaner but loses auto-capture.
            .setScannerMode(SCANNER_MODE_FULL)
            .build()
    }

    val scanner = remember(options) { GmsDocumentScanning.getClient(options) }

    val activityResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val data = result.data
                val scanResult = data?.let { GmsDocumentScanningResult.fromActivityResultIntent(it) }
                if (scanResult == null) {
                    onError(IllegalStateException("ML Kit scanner returned no result"))
                    return@rememberLauncherForActivityResult
                }
                val pages = scanResult.pages
                if (pages.isNullOrEmpty()) {
                    onCancelled()
                    return@rememberLauncherForActivityResult
                }
                for (page in pages) {
                    val bmp = decodePage(context, page.imageUri)
                    if (bmp != null) onPageScanned(bmp)
                }
                onSuccess()
            }
            Activity.RESULT_CANCELED -> onCancelled()
            else -> onError(IllegalStateException("ML Kit scanner failed: resultCode=${result.resultCode}"))
        }
    }

    return remember(scanner, activityResultLauncher) {
        DocumentScannerLauncher {
            val activity = context.findActivity()
                ?: return@DocumentScannerLauncher onError(IllegalStateException("Scanner needs an Activity context"))
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    activityResultLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener(onError)
        }
    }
}

/** Tiny invocable handle returned by [rememberDocumentScannerLauncher]. */
fun interface DocumentScannerLauncher {
    fun launch()
}

private fun decodePage(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (_: Throwable) {
        null
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
