package app.paperkeep.core.ml.summarizer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages lifecycle of the optional TFLite summarizer model file.
 *
 * The model is ~30 MB and is NOT bundled in the APK.
 * It is downloaded on first user opt-in and cached in `filesDir/models/`.
 *
 * Phase 5 ships with the heuristic extractive summarizer always available.
 * The TFLite neural summarizer is a future upgrade path — the download
 * infrastructure is wired here so the UI can show progress.
 *
 * Model is never required for correctness — [isModelAvailable] = false
 * means the heuristic path runs instead.
 */
@Singleton
class SummarizerModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val isModelAvailable: Boolean
        get() = modelFile.exists() && modelFile.length() > MIN_MODEL_SIZE_BYTES

    val modelFile: java.io.File
        get() = java.io.File(context.filesDir, "models/$MODEL_FILENAME")

    fun deleteModel() {
        modelFile.delete()
    }

    companion object {
        const val MODEL_FILENAME = "text_summarizer.tflite"
        private const val MIN_MODEL_SIZE_BYTES = 1_000L
    }
}
