package app.paperkeep.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.paperkeep.R
import app.paperkeep.core.ui.theme.PaperkeepTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent trampoline activity that handles ACTION_SEND / ACTION_SEND_MULTIPLE
 * shares from other apps (Gallery, Files, Chrome, etc.).
 *
 * Supported MIME types: image/jpeg, image/png, image/webp, image/heic, application/pdf.
 * Unsupported types show a toast and finish immediately.
 *
 * On success the user is handed off to the Paperkeep crop/review screen.
 */
@AndroidEntryPoint
class ShareImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = extractUris(intent)
        if (uris.isEmpty()) {
            Toast.makeText(this, getString(R.string.share_unsupported_type), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            PaperkeepTheme {
                Surface {
                    val scope = rememberCoroutineScope()
                    var processing by remember { mutableStateOf(true) }

                    LaunchedEffect(uris) {
                        scope.launch {
                            processSharedUris(uris)
                            processing = false
                        }
                    }

                    if (processing) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    private suspend fun processSharedUris(uris: List<Uri>) = withContext(Dispatchers.IO) {
        // Validate each URI: MIME type allowlist + 50 MB size cap.
        val imported = mutableListOf<Uri>()
        var tooLarge = false

        for (uri in uris) {
            when (classifyUri(uri)) {
                UriVerdict.ACCEPT -> imported.add(uri)
                UriVerdict.TOO_LARGE -> tooLarge = true
                UriVerdict.REJECT -> Unit
            }
        }

        withContext(Dispatchers.Main) {
            when {
                imported.isEmpty() && tooLarge ->
                    Toast.makeText(this@ShareImportActivity, getString(R.string.share_too_large), Toast.LENGTH_SHORT).show()
                imported.isEmpty() ->
                    Toast.makeText(this@ShareImportActivity, getString(R.string.share_unsupported_type), Toast.LENGTH_SHORT).show()
                else -> {
                    if (tooLarge) {
                        Toast.makeText(this@ShareImportActivity, getString(R.string.share_some_skipped), Toast.LENGTH_SHORT).show()
                    }
                    Toast.makeText(this@ShareImportActivity, getString(R.string.share_success), Toast.LENGTH_SHORT).show()
                    // Hand off to MainActivity with the import URIs
                    val launch = Intent(this@ShareImportActivity, app.paperkeep.MainActivity::class.java).apply {
                        action = "app.paperkeep.action.IMPORT_IMAGES"
                        putParcelableArrayListExtra("import_uris", ArrayList(imported))
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(launch)
                }
            }
            finish()
        }
    }

    private enum class UriVerdict { ACCEPT, TOO_LARGE, REJECT }

    /**
     * Classify a shared URI against the MIME allowlist and 50 MB size cap.
     * URIs we can't read are treated as [UriVerdict.REJECT].
     */
    private fun classifyUri(uri: Uri): UriVerdict = try {
        val type = contentResolver.getType(uri)
        val size = queryFileSize(uri)
        when {
            type == null || !isMimeAllowed(type) -> UriVerdict.REJECT
            size != null && size > MAX_IMPORT_BYTES -> UriVerdict.TOO_LARGE
            else -> UriVerdict.ACCEPT
        }
    } catch (_: Exception) {
        UriVerdict.REJECT
    }

    private fun isMimeAllowed(mime: String): Boolean =
        mime == "image/jpeg" ||
            mime == "image/png" ||
            mime == "image/webp" ||
            mime == "image/heic" ||
            mime == "image/heif" ||
            mime == "application/pdf"

    /** Query the file size via [ContentResolver] open-file descriptor. Returns null if unknown. */
    private fun queryFileSize(uri: Uri): Long? = try {
        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            pfd.statSize.takeIf { it >= 0 }
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        /** 50 MB maximum per imported file. */
        private const val MAX_IMPORT_BYTES = 50L * 1024 * 1024
    }

    private fun extractUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
                listOfNotNull(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("UNCHECKED_CAST")
                (intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM) as? List<Uri>)
                    ?: emptyList()
            }
            else -> emptyList()
        }
    }
}
