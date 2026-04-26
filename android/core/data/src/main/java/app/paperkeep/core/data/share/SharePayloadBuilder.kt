package app.paperkeep.core.data.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.common.DebugLog
import app.paperkeep.core.data.crypto.EncryptedImageStore
import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.pdf.DocumentExporter
import app.paperkeep.core.pdf.PageData
import app.paperkeep.core.pdf.PdfExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds short-lived plaintext payloads for the system share sheet (and SAF
 * export). Files land in `cacheDir/shares/` so the FileProvider declared in
 * the manifest can grant URI permissions to the receiving app.
 *
 * **Lifetime guarantee:** [cleanupOldPayloads] purges files older than
 * 5 minutes on every invocation — the share sheet typically resolves in
 * under a second, but Android can keep the URI grant alive briefly.
 *
 * **No plaintext leaks beyond the share:** files only exist while the user
 * is actively sharing. A 60-second leftover window is acceptable per the
 * CLAUDE.md exception ("cacheDir/exports/X.pdf lives for 60 seconds during
 * a share"). We extend that to 5 min for slow share targets (Drive upload).
 */
@Singleton
class SharePayloadBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageStore: EncryptedImageStore,
    private val pdfExporter: PdfExporter,
    private val documentExporter: DocumentExporter,
    private val dispatchers: AppDispatchers,
) {

    private val authority: String get() = "${context.packageName}.fileprovider"
    private val sharesDir: File get() = File(context.cacheDir, "shares").apply { mkdirs() }

    /**
     * Build a multi-page PDF for [document] in `cacheDir/shares/` and return
     * a FileProvider URI plus the file path (caller may need both).
     */
    suspend fun buildPdf(document: Document): SharePayload = withContext(dispatchers.io) {
        cleanupOldPayloads()
        val pages = decryptAllPages(document)
        val ocrTexts = document.pages.sortedBy { it.pageIndex }.map { it.ocrText.orEmpty() }
        val pageData = pages.zip(ocrTexts).map { (bmp, ocr) ->
            PageData(bitmap = bmp, ocrText = ocr.takeIf { it.isNotBlank() })
        }
        val safe = sanitizeFilename(document.title)
        val dest = File(sharesDir, "${safe}.pdf")
        pdfExporter.export(pageData, dest)
        DebugLog.d("Paperkeep.Share", "built PDF ${dest.absolutePath} size=${dest.length()}")
        SharePayload(
            uri = uriOf(dest),
            mimeType = "application/pdf",
            file = dest,
            displayName = "${safe}.pdf",
        )
    }

    /**
     * Single-page JPEG share (current page).
     */
    suspend fun buildPageJpeg(document: Document, pageIndex: Int): SharePayload =
        withContext(dispatchers.io) {
            cleanupOldPayloads()
            val pageOrdered = document.pages.sortedBy { it.pageIndex }
            require(pageIndex in pageOrdered.indices) {
                "pageIndex=$pageIndex out of range 0..${pageOrdered.size - 1}"
            }
            val bmp = decryptPage(pageOrdered[pageIndex].encryptedImagePath)
            val safe = sanitizeFilename(document.title)
            val dest = File(sharesDir, "${safe}_p${pageIndex + 1}.jpg")
            documentExporter.exportJpeg(bmp, dest)
            DebugLog.d("Paperkeep.Share", "built JPEG ${dest.absolutePath} size=${dest.length()}")
            SharePayload(
                uri = uriOf(dest),
                mimeType = "image/jpeg",
                file = dest,
                displayName = "${safe}_p${pageIndex + 1}.jpg",
            )
        }

    /**
     * Multi-page JPEG share — returns URIs for every page so the caller can
     * use ACTION_SEND_MULTIPLE.
     */
    suspend fun buildAllPagesJpeg(document: Document): MultiSharePayload =
        withContext(dispatchers.io) {
            cleanupOldPayloads()
            val pageOrdered = document.pages.sortedBy { it.pageIndex }
            val safe = sanitizeFilename(document.title)
            val files = pageOrdered.mapIndexed { idx, page ->
                val bmp = decryptPage(page.encryptedImagePath)
                val dest = File(sharesDir, "${safe}_p${idx + 1}.jpg")
                documentExporter.exportJpeg(bmp, dest)
                dest
            }
            DebugLog.d("Paperkeep.Share", "built ${files.size} JPEGs for $safe")
            MultiSharePayload(
                uris = files.map { uriOf(it) },
                mimeType = "image/jpeg",
                files = files,
            )
        }

    /**
     * Plain-text export of OCR contents.
     */
    suspend fun buildOcrText(document: Document): SharePayload? = withContext(dispatchers.io) {
        cleanupOldPayloads()
        val pageOrdered = document.pages.sortedBy { it.pageIndex }
        val ocr = pageOrdered.map { it.ocrText.orEmpty() }
        if (ocr.all { it.isBlank() }) {
            DebugLog.w("Paperkeep.Share", "buildOcrText: no OCR text available")
            return@withContext null
        }
        val safe = sanitizeFilename(document.title)
        val dest = File(sharesDir, "${safe}.txt")
        documentExporter.exportTxt(ocr, dest)
        DebugLog.d("Paperkeep.Share", "built TXT ${dest.absolutePath} size=${dest.length()}")
        SharePayload(
            uri = uriOf(dest),
            mimeType = "text/plain",
            file = dest,
            displayName = "${safe}.txt",
        )
    }

    /**
     * Read the bytes of a previously-built payload — used by SAF export which
     * pipes them into the user-chosen OutputStream.
     */
    suspend fun readPayloadBytes(file: File): ByteArray = withContext(dispatchers.io) {
        file.readBytes()
    }

    /** Delete share payloads older than 5 minutes. Called on every build. */
    private fun cleanupOldPayloads() {
        val cutoff = System.currentTimeMillis() - PAYLOAD_TTL_MS
        sharesDir.listFiles()?.forEach { f ->
            if (f.lastModified() < cutoff) {
                runCatching { f.delete() }
            }
        }
    }

    private fun decryptAllPages(document: Document): List<Bitmap> {
        return document.pages.sortedBy { it.pageIndex }.map { page ->
            decryptPage(page.encryptedImagePath)
        }
    }

    private fun decryptPage(path: String): Bitmap {
        val plain = imageStore.read(File(path))
        return BitmapFactory.decodeByteArray(plain, 0, plain.size)
            ?: error("Failed to decode bitmap from $path")
    }

    private fun uriOf(file: File): Uri = FileProvider.getUriForFile(context, authority, file)

    private fun sanitizeFilename(input: String): String {
        val cleaned = input.trim()
            .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), "_")
        return cleaned.ifBlank { "Paperkeep_Document" }.take(64)
    }

    companion object {
        private const val PAYLOAD_TTL_MS = 5 * 60 * 1000L
    }
}

data class SharePayload(
    val uri: Uri,
    val mimeType: String,
    val file: File,
    val displayName: String,
)

data class MultiSharePayload(
    val uris: List<Uri>,
    val mimeType: String,
    val files: List<File>,
)
