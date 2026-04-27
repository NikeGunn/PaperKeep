package app.paperkeep.core.backup.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P4.6 — per-bucket disk-usage breakdown for the storage manager UI.
 *
 * Buckets:
 *   - SCANS     filesDir/scans/        (encrypted page + thumb files)
 *   - OCR       filesDir/page_ocr/     (encrypted OCR blobs, if used)
 *   - EXPORTS   cacheDir/exports/      (transient PDF/JPEG share payloads)
 *   - SHARES    cacheDir/shares/       (transient share-sheet payloads)
 *   - CRASH     filesDir/crash/        (encrypted crash logs)
 *   - OTHER     anything in filesDir we did not explicitly bucket
 */
data class StorageReport(
    val scansBytes: Long,
    val ocrBytes: Long,
    val exportsBytes: Long,
    val sharesBytes: Long,
    val crashBytes: Long,
    val otherBytes: Long,
) {
    val totalBytes: Long
        get() = scansBytes + ocrBytes + exportsBytes + sharesBytes + crashBytes + otherBytes
}

@Singleton
class StorageReporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun report(): StorageReport {
        val files = context.filesDir
        val cache = context.cacheDir

        val scans = sizeOfDir(File(files, "scans"))
        val ocr = sizeOfDir(File(files, "page_ocr"))
        val exports = sizeOfDir(File(cache, "exports"))
        val shares = sizeOfDir(File(cache, "shares"))
        val crash = sizeOfDir(File(files, "crash"))

        // "Other" = everything in filesDir we didn't account for. We do NOT
        // count caches outside our two bucket dirs because they're typically
        // glide/coil scratch we don't manage here.
        val accountedFor = setOf("scans", "page_ocr", "crash")
        val other = files.listFiles()
            ?.filter { it.name !in accountedFor }
            ?.sumOf { sizeOfDir(it) }
            ?: 0L

        return StorageReport(
            scansBytes = scans,
            ocrBytes = ocr,
            exportsBytes = exports,
            sharesBytes = shares,
            crashBytes = crash,
            otherBytes = other,
        )
    }

    /** P4.6 action — clear transient share/export payloads. Returns bytes freed. */
    fun clearTransientCaches(): Long {
        val cache = context.cacheDir
        val before = sizeOfDir(File(cache, "exports")) + sizeOfDir(File(cache, "shares"))
        File(cache, "exports").deleteRecursively()
        File(cache, "shares").deleteRecursively()
        return before
    }

    /** P4.6 action — empty the crash-log bucket. Returns bytes freed. */
    fun clearCrashLogs(): Long {
        val dir = File(context.filesDir, "crash")
        val before = sizeOfDir(dir)
        dir.deleteRecursively()
        return before
    }

    companion object {
        fun sizeOfDir(dir: File): Long {
            if (!dir.exists()) return 0L
            if (dir.isFile) return dir.length()
            return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }
}
