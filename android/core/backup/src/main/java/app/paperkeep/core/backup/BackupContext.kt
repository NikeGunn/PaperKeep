package app.paperkeep.core.backup

import app.paperkeep.core.backup.format.BackupSettings
import app.paperkeep.core.data.db.DocumentWithPages
import java.io.File

/**
 * Pure-data input to [BackupEngine.write]. The engine does not touch Hilt /
 * Context / Room directly so it stays unit-testable on the JVM.
 *
 * [resolveEncryptedFile] returns the absolute on-disk file backing a page's
 * `encryptedImagePath` (which is stored relative to `filesDir`). The engine
 * decrypts each file via [decryptPage] and re-encrypts it inside the backup
 * stream — we do NOT ship the user's K_master ciphertext directly because the
 * backup is portable and must work on a re-installed app whose Keystore key is
 * different.
 */
data class BackupInput(
    val documents: List<DocumentWithPages>,
    val settings: BackupSettings,
    val dbBytes: ByteArray,
    val appVersionName: String,
    val appVersionCode: Int,
    val resolveEncryptedFile: (String) -> File,
    val decryptPage: (File) -> ByteArray,
)

/**
 * Result of a successful backup write.
 *
 * [bytesWritten] counts the entire on-disk file (header + ciphertext).
 * [sha256Hex] is the digest of those same bytes — store it alongside
 * [BackupEntity] for tamper-detection in the backup-history list.
 */
data class BackupOutput(
    val bytesWritten: Long,
    val sha256Hex: String,
    val documentCount: Int,
    val pageCount: Int,
    val schemaVersion: Int,
)

/** Result of a successful restore. */
data class RestoreOutput(
    val documentCount: Int,
    val pageCount: Int,
    val restoredFolderName: String,
    val settings: BackupSettings,
)

/**
 * Conflict resolution strategy for restore (P4.5).
 */
enum class RestoreStrategy {
    /** Keep existing data; new docs land alongside in a "Restored YYYY-MM-DD" folder. */
    MERGE,

    /** Wipe existing documents/pages first, then import the backup as primary library. */
    REPLACE,
}
