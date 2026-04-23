package app.paperkeep.core.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * ML Kit Text Recognition v2 implementation of [OcrEngine].
 *
 * Uses the Latin bundled recogniser — no model download required.
 * The [TextRecognizer] is lazy-created once and reused (thread-safe per ML Kit docs).
 */
@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognise(bitmap: Bitmap): String {
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) return ""
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            processImage(image)
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun processImage(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText.text)
                }
                .addOnFailureListener {
                    cont.resume("")
                }
        }
}
