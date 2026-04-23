package app.paperkeep.core.ml

import android.graphics.Bitmap

/**
 * Deterministic [OcrEngine] for use in unit tests.
 *
 * Behaviour is controlled by the constructor parameters:
 *  - [textToReturn]: text to return for any valid (non-recycled, non-empty) bitmap.
 *  - [emptyText]: when true, always returns "" regardless of bitmap content.
 *
 * This design avoids relying on Robolectric pixel rendering (Canvas shadows
 * don't always write real pixel data in JVM unit tests).
 */
class FakeOcrEngine(
    private val textToReturn: String = "Hello World",
    private val emptyText: Boolean = false,
) : OcrEngine {

    override suspend fun recognise(bitmap: Bitmap): String {
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) return ""
        if (emptyText) return ""
        return textToReturn
    }
}
