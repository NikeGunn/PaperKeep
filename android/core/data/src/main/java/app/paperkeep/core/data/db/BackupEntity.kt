package app.paperkeep.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per successful backup written via SAF (P4.2).
 *
 * [safUri] is the persistable SAF URI returned by [DocumentFile] / `ContentResolver
 * .takePersistableUriPermission`. It survives reboots so we can re-list / re-open
 * the backup without re-prompting the user. **It does not survive uninstall.**
 *
 * [sha256] is computed over the raw on-disk bytes (header + ciphertext) — used by
 * the backup-history UI to detect tampered or rotated files since they were written.
 */
@Entity(
    tableName = "backups",
    indices = [Index("createdAt")],
)
data class BackupEntity(
    @PrimaryKey val id: String,
    /** Persistable SAF URI of the on-disk backup file. */
    val safUri: String,
    /** Display name shown in the SAF picker (e.g. "Paperkeep_2026-04-26.pkbk"). */
    val displayName: String,
    /** Epoch millis when the backup was completed. */
    val createdAt: Long,
    /** Number of documents included in this backup. */
    val documentCount: Int,
    /** Number of pages included. */
    val pageCount: Int,
    /** Plaintext byte size of the backup file (header + ciphertext). */
    val sizeBytes: Long,
    /** SHA-256 hex of the backup file as written. */
    val sha256: String,
    /** Schema version of the manifest (mirrors [BackupManifest.schemaVersion]). */
    val schemaVersion: Int,
)
