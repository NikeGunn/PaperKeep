package app.paperkeep.core.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.paperkeep.core.data.db.OcrStatus
import app.paperkeep.core.data.repository.DocumentRepository
import app.paperkeep.core.domain.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the OCR pipeline for pending pages.
 *
 * Responsibilities:
 *  1. Load pages with ocrStatus = PENDING from the repository.
 *  2. Decode the plaintext bitmap (caller must supply decrypted bytes).
 *  3. Run [OcrEngine.recognise] on [Dispatchers.Default].
 *  4. Write status + inline text back to the DB ([DocumentRepository.updateOcrStatus]).
 *  5. Index the plaintext into the HMAC'd FTS4 table ([DocumentRepository.indexOcrForSearch]).
 *
 * The encrypted image store is intentionally NOT accessed here — the scanner
 * layer decrypts images and passes raw bitmaps or bytes. This keeps `:core:ml`
 * free from a dependency on `:core:data` crypto internals.
 *
 * Callers:
 *  - Scanner screen (after capture → perspective-correct → save → trigger OCR).
 *  - Background work (retry FAILED pages on next app open).
 */
@Singleton
class OcrOrchestrator @Inject constructor(
    private val ocrEngine: OcrEngine,
    private val repo: DocumentRepository,
    private val bitmapDecoder: suspend (ByteArray) -> Bitmap? = { bytes ->
        withContext(Dispatchers.Default) {
            if (bytes.isEmpty()) null
            else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    },
) {

    /**
     * Run OCR on [decodedBytes] for [page] and persist the result.
     *
     * Must be called from a coroutine. Internally switches to [Dispatchers.Default]
     * for the CPU-bound decode + recognition step.
     *
     * Never throws — errors are recorded as [OcrStatus.FAILED] in the DB.
     */
    suspend fun processPage(page: Page, decodedBytes: ByteArray) {
        try {
            val bitmap = bitmapDecoder(decodedBytes)
            if (bitmap == null) {
                repo.updateOcrStatus(page.id, OcrStatus.FAILED, null)
                return
            }

            val text = withContext(Dispatchers.Default) {
                ocrEngine.recognise(bitmap)
            }

            val detectedLanguage = if (text.isNotBlank()) "en" else null
            repo.updateOcrStatus(page.id, OcrStatus.DONE, detectedLanguage)
            repo.updateOcrText(page.id, text.ifBlank { null })

            if (text.isNotBlank()) {
                repo.indexOcrForSearch(page.id, text)
                repo.refreshFtsRow(page.documentId)
            }
        } catch (_: Exception) {
            repo.updateOcrStatus(page.id, OcrStatus.FAILED, null)
        }
    }

    /**
     * Retry all FAILED or PENDING pages in the repository (up to [limit]).
     *
     * Callers supply a [decodedBytesProvider] that decrypts the image for each
     * page — this keeps the crypto layer out of `:core:ml`.
     */
    suspend fun retryPendingPages(
        limit: Int = 20,
        decodedBytesProvider: suspend (Page) -> ByteArray?,
    ) {
        val pages = repo.getPendingOcrPages(limit)
        pages.forEach { page ->
            val bytes = decodedBytesProvider(page) ?: run {
                repo.updateOcrStatus(page.id, OcrStatus.FAILED, null)
                return@forEach
            }
            processPage(page, bytes)
        }
    }
}
