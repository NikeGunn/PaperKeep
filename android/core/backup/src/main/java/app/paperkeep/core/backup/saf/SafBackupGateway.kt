package app.paperkeep.core.backup.saf

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/**
 * Thin wrapper over [ContentResolver] for SAF I/O so [BackupEngine] tests do
 * not require a real Android Context.
 *
 * The CREATE/OPEN intents themselves are launched from the UI layer via the
 * Activity Result API — see [SafIntents]. This class only deals with the URI
 * once the picker has returned it.
 */
interface SafBackupGateway {
    fun openOutputStream(uri: Uri): OutputStream?
    fun openInputStream(uri: Uri): InputStream?
    fun takePersistableUriPermission(uri: Uri, flags: Int)
    fun displayName(uri: Uri): String?
}

class ContentResolverSafGateway(
    private val resolver: ContentResolver,
) : SafBackupGateway {
    override fun openOutputStream(uri: Uri): OutputStream? = resolver.openOutputStream(uri, "w")
    override fun openInputStream(uri: Uri): InputStream? = resolver.openInputStream(uri)
    override fun takePersistableUriPermission(uri: Uri, flags: Int) {
        try {
            resolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // SAF source did not grant persistable rights — backup will still
            // work for this session but won't survive a reboot. Surface in UI.
        }
    }

    override fun displayName(uri: Uri): String? {
        return runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }
}

/** Intent factories so screens / ViewModels stay decoupled from SAF specifics. */
object SafIntents {

    const val MIME_BACKUP: String = "application/octet-stream"

    /** ACTION_CREATE_DOCUMENT for a new backup file. */
    fun create(suggestedFilename: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = MIME_BACKUP
            putExtra(Intent.EXTRA_TITLE, suggestedFilename)
            // Persistent permissions so we can re-open from history later.
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }

    /** ACTION_OPEN_DOCUMENT to pick an existing backup file for restore. */
    fun open(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Allow octet-stream, all files, and any user-named extension.
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(MIME_BACKUP, "application/zip"))
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }

    /** Default backup filename: `Paperkeep_2026-04-26_153012.pkbk`. */
    fun defaultFilename(epochMillis: Long = System.currentTimeMillis()): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.US)
        return "Paperkeep_${sdf.format(java.util.Date(epochMillis))}.pkbk"
    }
}
