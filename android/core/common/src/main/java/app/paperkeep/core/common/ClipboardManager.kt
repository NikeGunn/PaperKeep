package app.paperkeep.core.common

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P2.15 — Clipboard hygiene for OCR text copies.
 *
 * Two security properties:
 *  1. On API 33+ sets [ClipDescription.EXTRA_IS_SENSITIVE] = true so the system
 *     keyboard and clipboard manager redact the content in their UIs.
 *  2. Schedules auto-clear of the clipboard after [AUTO_CLEAR_DELAY_MS] (60 s) if
 *     the clipboard still holds our content.
 *
 * Both properties defend against clipboard-snooping apps and shoulder-surfing
 * on the system clipboard history.
 */
@Singleton
class PaperkeepClipboardManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val systemClipboard: ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val scope = CoroutineScope(Dispatchers.Main)
    private var autoClearJob: Job? = null

    /**
     * Copy [text] to the system clipboard with sensitivity marking and 60-second auto-clear.
     *
     * @param label Human-readable label shown in clipboard history (e.g. "OCR text").
     *              Kept short; does not contain document content.
     */
    fun copyOcrText(text: String, label: String = "OCR text") {
        val clip = ClipData.newPlainText(label, text)

        // API 33+: mark as sensitive so system clipboard manager redacts it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }

        systemClipboard.setPrimaryClip(clip)
        scheduleAutoClear(text)
    }

    /**
     * Immediately clears the clipboard if it currently holds our content.
     * Safe to call at any time (no-op if clipboard has been replaced by another app).
     */
    fun clearIfOurs(text: String) {
        autoClearJob?.cancel()
        val current = systemClipboard.primaryClip
            ?.getItemAt(0)
            ?.text
            ?.toString()
        if (current == text) {
            clearClipboard()
        }
    }

    /** Cancel any pending auto-clear job. Call when the user navigates away. */
    fun cancelAutoClear() {
        autoClearJob?.cancel()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun scheduleAutoClear(text: String) {
        autoClearJob?.cancel()
        autoClearJob = scope.launch {
            delay(AUTO_CLEAR_DELAY_MS)
            clearIfOurs(text)
        }
    }

    private fun clearClipboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            systemClipboard.clearPrimaryClip()
        } else {
            // Pre-API 28: replace with empty clip (no clearPrimaryClip API)
            systemClipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    companion object {
        const val AUTO_CLEAR_DELAY_MS = 60_000L
    }
}
