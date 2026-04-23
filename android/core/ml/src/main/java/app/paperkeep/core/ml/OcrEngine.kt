package app.paperkeep.core.ml

import android.graphics.Bitmap

/**
 * On-device OCR contract.
 *
 * Implementations must be thread-safe and may be called from any dispatcher.
 * [recognise] always returns, even for images with no text (empty string) or
 * corrupted inputs (returns empty string rather than throwing).
 */
interface OcrEngine {
    /**
     * Run text recognition on [bitmap] and return the extracted text.
     *
     * Returns an empty string when no text is detected.
     * Never throws — callers can rely on this contract.
     */
    suspend fun recognise(bitmap: Bitmap): String
}
