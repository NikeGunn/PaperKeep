package com.scanvault.core.ml

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TFLite-backed document type classifier (3B.1).
 *
 * The model is lazily loaded on the first [classify] call. If no model file is
 * present the stub falls back to a heuristic that always returns
 * [DocumentType.DOCUMENT] with a confidence of 0.5 — the app degrades
 * gracefully rather than crashing.
 *
 * Model file (assets/document_classifier.tflite) is optional for Phase 3B;
 * it will be shipped in Phase 4C when the intelligence layer is connected.
 */
@Singleton
class DocumentClassifier @Inject constructor() {

    /**
     * Whether the TFLite interpreter has been initialised.
     * Exposed for testing the lazy-load contract.
     */
    @Volatile
    var modelLoaded: Boolean = false
        private set

    /**
     * Classify [bitmap] and return a [ClassificationResult].
     *
     * If the model is not available the stub heuristic is used instead.
     * This function is safe to call from any coroutine dispatcher.
     */
    fun classify(bitmap: Bitmap): ClassificationResult {
        ensureModelLoaded()
        return classifyInternal(bitmap)
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Lazy-init guard. Sets [modelLoaded] = true on first call.
     * In the stub implementation there is no real interpreter to create.
     */
    private fun ensureModelLoaded() {
        if (!modelLoaded) {
            synchronized(this) {
                if (!modelLoaded) {
                    // Phase 3B stub — no .tflite file needed yet.
                    // Phase 4C will replace this with an actual TFLite interpreter.
                    modelLoaded = true
                }
            }
        }
    }

    /**
     * Stub inference. Returns [DocumentType.DOCUMENT] with confidence 0.5.
     *
     * A real implementation would feed the bitmap through the interpreter and
     * read softmax scores from the output tensor.
     */
    private fun classifyInternal(@Suppress("UNUSED_PARAMETER") bitmap: Bitmap): ClassificationResult {
        // Aspect-ratio heuristic: if height >> width treat as receipt
        val ratio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val type = when {
            ratio >= 2.5f -> DocumentType.RECEIPT
            else -> DocumentType.DOCUMENT
        }
        return ClassificationResult(type = type, confidence = 0.5f)
    }
}

/** Supported document categories. */
enum class DocumentType {
    DOCUMENT,
    RECEIPT,
    ID_CARD,
    WHITEBOARD,
    UNKNOWN,
}

/**
 * Result of a single classification inference.
 *
 * @param type     The predicted [DocumentType].
 * @param confidence Normalised score in `[0.0, 1.0]`.
 */
data class ClassificationResult(
    val type: DocumentType,
    val confidence: Float,
) {
    init {
        require(confidence in 0f..1f) {
            "confidence must be in [0.0, 1.0] but was $confidence"
        }
    }
}
