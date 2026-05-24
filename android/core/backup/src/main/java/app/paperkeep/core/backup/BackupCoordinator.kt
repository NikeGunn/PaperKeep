package app.paperkeep.core.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import app.paperkeep.core.backup.format.BackupManifest
import app.paperkeep.core.backup.format.BackupSettings
import app.paperkeep.core.backup.saf.SafBackupGateway
import app.paperkeep.core.data.crypto.EncryptedImageStore
import app.paperkeep.core.data.db.BackupDao
import app.paperkeep.core.data.db.BackupEntity
import app.paperkeep.core.data.db.DocumentDao
import app.paperkeep.core.data.db.DocumentEntity
import app.paperkeep.core.data.db.DocumentWithPages
import app.paperkeep.core.data.db.FolderEntity
import app.paperkeep.core.data.db.PageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-side glue: pulls Document/Page/Folder rows + decrypts page files
 * + serializes settings + dumps the SQLite DB → hands the assembled
 * [BackupInput] to [BackupEngine.write], then records a [BackupEntity] row.
 *
 * Restore is the symmetric path: it streams from a SAF [Uri], lets the engine
 * parse manifest+DB+settings+pages, then re-encrypts pages with the current
 * Keystore key and inserts new Document/Page rows.
 *
 * No ViewModel or Compose code lives here — the screens drive this via
 * [feature:settings] view models.
 */
@Singleton
class BackupCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDao: DocumentDao,
    private val backupDao: BackupDao,
    private val imageStore: EncryptedImageStore,
    private val engine: BackupEngine,
    private val safGateway: SafBackupGateway,
) {

    /** Snapshot the live DB and produce a backup at [destinationUri]. */
    suspend fun runBackup(
        destinationUri: Uri,
        password: CharArray,
        settings: BackupSettings,
    ): BackupOutput = withContext(Dispatchers.IO) {
        val docs = documentDao.observeAllWithPages().first()
        val dbBytes = readDatabaseBytes()

        val out = safGateway.openOutputStream(destinationUri)
            ?: throw java.io.FileNotFoundException("SAF returned no output stream for $destinationUri")

        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
        val versionCode = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            // longVersionCode is API 28+; minSdk is 26. The backup stores an Int,
            // so fall back to the legacy versionCode field on API 26/27 to avoid
            // a NoSuchMethodError crash on those devices.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        }.getOrDefault(0)

        val input = BackupInput(
            documents = docs,
            settings = settings,
            dbBytes = dbBytes,
            appVersionName = versionName,
            appVersionCode = versionCode,
            resolveEncryptedFile = { rel -> File(context.filesDir, rel) },
            decryptPage = { f -> imageStore.read(f) },
        )

        val result = out.use { sink ->
            engine.write(input = input, sink = sink, password = password)
        }

        // Persist a BackupEntity row in the DB for the history list.
        val displayName = safGateway.displayName(destinationUri)
            ?: "Paperkeep_backup.pkbk"
        val row = BackupEntity(
            id = UUID.randomUUID().toString(),
            safUri = destinationUri.toString(),
            displayName = displayName,
            createdAt = System.currentTimeMillis(),
            documentCount = result.documentCount,
            pageCount = result.pageCount,
            sizeBytes = result.bytesWritten,
            sha256 = result.sha256Hex,
            schemaVersion = result.schemaVersion,
        )
        backupDao.insert(row)

        result
    }

    /** Read a previously written backup and import it per [strategy]. */
    suspend fun runRestore(
        sourceUri: Uri,
        password: CharArray,
        strategy: RestoreStrategy,
    ): RestoreOutput = withContext(Dispatchers.IO) {
        val input = safGateway.openInputStream(sourceUri)
            ?: throw java.io.FileNotFoundException("SAF returned no input stream for $sourceUri")

        var manifest: BackupManifest? = null
        var settings: BackupSettings? = null
        // We collect page bytes here keyed by zipPath; the manifest tells us
        // which doc/page each one belongs to so we don't depend on order.
        val pages = mutableMapOf<String, ByteArray>()

        input.use { stream ->
            engine.read(stream, password, object : BackupReadVisitor {
                override fun onManifest(m: BackupManifest) { manifest = m }
                override fun onDatabase(bytes: ByteArray) {
                    // We do NOT overwrite the live DB — too risky. We import
                    // documents/pages explicitly via the manifest below. The
                    // DB blob is preserved on the backup file for forensic /
                    // alternative-tool restore but unused here.
                }
                override fun onSettings(s: BackupSettings) { settings = s }
                override fun onPage(zipPath: String, plaintextJpeg: ByteArray) {
                    // Verify SHA-256 in the post-pass (need manifest first).
                    pages[zipPath] = plaintextJpeg
                }
            })
        }

        val m = manifest ?: throw IllegalStateException("backup is missing manifest.json")

        // Verify each page's SHA-256 against the manifest claim. A mismatch
        // means the inner ZIP entry was tampered with. GCM auth would catch
        // any change to the OUTER ciphertext; this catches re-zip-after-decrypt.
        for (ref in m.pages) {
            val bytes = pages[ref.zipPath]
                ?: throw IllegalStateException("backup is missing page ${ref.zipPath}")
            val sha = sha256Hex(bytes)
            if (sha != ref.sha256) {
                throw IllegalStateException("page ${ref.zipPath} integrity check failed")
            }
        }

        if (strategy == RestoreStrategy.REPLACE) {
            // Wipe existing docs + their pages cascade-delete via FK. Folders
            // are kept — user-created and may be referenced by other things.
            val existing = documentDao.observeAllWithPages().first()
            for (doc in existing) documentDao.deleteDocument(doc.document)
        }

        // Create the destination folder for restored documents.
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val folderName = "Restored ${sdf.format(Date(m.createdAtMs))}"
        val folderId = UUID.randomUUID().toString()
        documentDao.insertFolder(
            FolderEntity(
                id = folderId,
                name = folderName,
                icon = "folder",
                autoRule = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )

        // Group manifest pages by documentId so we can rebuild documents
        // even though we don't carry the DocumentEntity in the wire format.
        val now = System.currentTimeMillis()
        val byDoc = m.pages.groupBy { it.documentId }
        for ((origDocId, refs) in byDoc) {
            val newDocId = UUID.randomUUID().toString()
            documentDao.insertDocument(
                DocumentEntity(
                    id = newDocId,
                    title = "Restored document ($origDocId)",
                    createdAt = now,
                    updatedAt = now,
                    folderId = folderId,
                    pageCount = refs.size,
                    colorTag = null,
                    docType = null,
                    isFavorite = false,
                    isArchived = false,
                ),
            )
            for (ref in refs.sortedBy { it.pageIndex }) {
                val bytes = pages[ref.zipPath]!!
                val newPageId = UUID.randomUUID().toString()
                val rel = "scans/$newDocId/$newPageId.enc"
                imageStore.write(File(context.filesDir, rel), bytes)
                val thumbRel = "scans/$newDocId/${newPageId}_thumb.enc"
                imageStore.write(File(context.filesDir, thumbRel), bytes) // OK: thumb uses same bytes — Phase 4 keeps it simple
                documentDao.insertPage(
                    PageEntity(
                        id = newPageId,
                        documentId = newDocId,
                        pageIndex = ref.pageIndex,
                        encryptedImagePath = rel,
                        encryptedThumbPath = thumbRel,
                        ocrStatus = "pending",
                        ocrLanguage = null,
                        ocrText = null,
                        width = 0,
                        height = 0,
                        filter = "original",
                    ),
                )
            }
        }

        RestoreOutput(
            documentCount = byDoc.size,
            pageCount = m.pages.size,
            restoredFolderName = folderName,
            settings = settings ?: BackupSettings(),
        )
    }

    private fun readDatabaseBytes(): ByteArray {
        val dbPath = context.getDatabasePath(DB_FILENAME)
        if (!dbPath.exists()) return ByteArray(0)
        return dbPath.readBytes()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /** Number of docs the next backup would include. */
    suspend fun documentCount(): Int = documentDao.countDocuments()

    /** Most recent successful backup, if any. */
    suspend fun mostRecent(): BackupEntity? = backupDao.getMostRecent()

    fun observeAllBackups() = backupDao.observeAll()

    private fun List<DocumentWithPages>.totalPages(): Int =
        sumOf { it.pages.size }

    companion object {
        private const val DB_FILENAME = "Paperkeep.db"
    }
}
