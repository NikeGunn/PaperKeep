package app.paperkeep.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for Paperkeep.
 *
 * Version history:
 *  1 → 2: Added folders, documents, pages tables.
 *  2 → 3: Added documents_fts FTS4 virtual table.
 *  3 → 4: Added syncStatus column to documents (later superseded).
 *  4 → 5: P2.1 data-model audit.
 *          - documents: added docType, isFavorite, isArchived; removed syncStatus.
 *          - pages: renamed imagePath→encryptedImagePath, thumbPath→encryptedThumbPath;
 *                   added ocrStatus, ocrLanguage.
 *          - folders: added icon, autoRule.
 *          - page_ocr: new table (encrypted OCR blobs + bboxes).
 *  5 → 6: P2.2 — added page_ocr_fts FTS4 virtual table for HMAC'd OCR search.
 */
@Database(
    entities = [
        ScanEntity::class,
        FolderEntity::class,
        DocumentEntity::class,
        PageEntity::class,
        PageOcrEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class PaperkeepDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun documentDao(): DocumentDao

    companion object {

        /**
         * Migration 4 → 5 (P2.1 data-model audit).
         *
         * Because SQLite does not support DROP COLUMN or RENAME COLUMN on all API
         * levels we target (min SDK 26), column renames are done via a table-copy
         * strategy (new table → copy → drop old → rename new).
         *
         * Documents:
         *  - DROP syncStatus (no longer meaningful — no backend)
         *  - ADD docType TEXT (nullable)
         *  - ADD isFavorite INTEGER NOT NULL DEFAULT 0
         *  - ADD isArchived INTEGER NOT NULL DEFAULT 0
         *  - ADD indices for isFavorite, isArchived, docType
         *
         * Pages (rename two columns + add two):
         *  - RENAME imagePath → encryptedImagePath
         *  - RENAME thumbPath → encryptedThumbPath
         *  - ADD ocrStatus TEXT NOT NULL DEFAULT 'pending'
         *  - ADD ocrLanguage TEXT (nullable)
         *  - ADD index on ocrStatus
         *
         * Folders:
         *  - ADD icon TEXT NOT NULL DEFAULT 'folder'
         *  - ADD autoRule TEXT (nullable)
         *
         * page_ocr:
         *  - CREATE TABLE page_ocr (pageId TEXT PK, encryptedText BLOB NOT NULL)
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ── 1. documents table ──────────────────────────────────────────
                // SQLite on API 26-28 cannot drop columns, so we do a table-copy.
                db.execSQL(
                    """
                    CREATE TABLE documents_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        folderId TEXT,
                        pageCount INTEGER NOT NULL,
                        colorTag INTEGER,
                        docType TEXT,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(folderId) REFERENCES folders(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO documents_new
                        (id, title, createdAt, updatedAt, folderId, pageCount, colorTag,
                         docType, isFavorite, isArchived)
                    SELECT id, title, createdAt, updatedAt, folderId, pageCount, colorTag,
                           NULL, 0, 0
                    FROM documents
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE documents")
                db.execSQL("ALTER TABLE documents_new RENAME TO documents")

                // Recreate indices for documents
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_createdAt ON documents(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_folderId ON documents(folderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_updatedAt ON documents(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_isFavorite ON documents(isFavorite)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_isArchived ON documents(isArchived)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_docType ON documents(docType)")

                // ── 2. pages table (rename columns + add new) ───────────────────
                db.execSQL(
                    """
                    CREATE TABLE pages_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        documentId TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        encryptedImagePath TEXT NOT NULL,
                        encryptedThumbPath TEXT NOT NULL,
                        ocrStatus TEXT NOT NULL DEFAULT 'pending',
                        ocrLanguage TEXT,
                        ocrText TEXT,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        filter TEXT NOT NULL DEFAULT 'original',
                        FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO pages_new
                        (id, documentId, pageIndex, encryptedImagePath, encryptedThumbPath,
                         ocrStatus, ocrLanguage, ocrText, width, height, filter)
                    SELECT id, documentId, pageIndex, imagePath, thumbPath,
                           CASE WHEN ocrText IS NOT NULL THEN 'done' ELSE 'pending' END,
                           NULL, ocrText, width, height, filter
                    FROM pages
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE pages")
                db.execSQL("ALTER TABLE pages_new RENAME TO pages")

                // Recreate indices for pages
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pages_documentId ON pages(documentId)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_pages_documentId_pageIndex ON pages(documentId, pageIndex)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pages_ocrStatus ON pages(ocrStatus)")

                // ── 3. folders table (additive — SQLite ALTER TABLE ADD COLUMN ok) ─
                db.execSQL("ALTER TABLE folders ADD COLUMN icon TEXT NOT NULL DEFAULT 'folder'")
                db.execSQL("ALTER TABLE folders ADD COLUMN autoRule TEXT")

                // ── 4. page_ocr table ──────────────────────────────────────────
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS page_ocr (
                        pageId TEXT NOT NULL PRIMARY KEY,
                        encryptedText BLOB NOT NULL,
                        FOREIGN KEY(pageId) REFERENCES pages(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // ── 5. Rebuild FTS index so existing docs still find-able ──────
                db.execSQL("DELETE FROM documents_fts")
                db.execSQL(
                    """
                    INSERT INTO documents_fts(docId, title, ocrText)
                    SELECT d.id,
                           d.title,
                           COALESCE((SELECT GROUP_CONCAT(p.ocrText, ' ')
                                     FROM pages p
                                     WHERE p.documentId = d.id
                                       AND p.ocrText IS NOT NULL), '')
                    FROM documents d
                    """.trimIndent()
                )
            }
        }

        /**
         * Migration 5 → 6 (P2.2 HMAC'd OCR search index).
         *
         * Creates the page_ocr_fts FTS4 virtual table.  It stores:
         *   pageId   – back-reference to pages.id
         *   tokens   – space-joined HMAC-SHA256 hex tokens (see OcrFtsIndex)
         *
         * The table is intentionally NOT pre-populated here; OcrOrchestrator
         * populates it on the next OCR run for existing pages.
         */
        /**
         * Migration 6 → 7: Ensure both FTS virtual tables exist.
         *
         * These tables were created in MIGRATION_2_3 and MIGRATION_5_6 respectively,
         * but a bug in the original DataModule meant they were never created on fresh
         * installs (Room's onCreate creates schema from entities only; virtual tables
         * are not in @Database(entities=[])). This migration is a no-op for DBs that
         * already have the tables; for others it creates them now.
         */
        /**
         * Migration 6 → 7: Ensure both FTS virtual tables exist.
         *
         * FTS4 reserves 'docid' as an internal rowid alias; creating a column
         * named 'docId' (case-insensitive match) causes a SQLite error on some
         * API levels. We check existence via sqlite_master before attempting
         * creation so this migration is safe to run on DBs that already have
         * the tables (avoids the IF NOT EXISTS + FTS4 conflict).
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasFts = db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='documents_fts'"
                ).use { it.count > 0 }
                if (!hasFts) {
                    db.execSQL(
                        "CREATE VIRTUAL TABLE documents_fts USING fts4(docId, title, ocrText)"
                    )
                }
                val hasOcrFts = db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='page_ocr_fts'"
                ).use { it.count > 0 }
                if (!hasOcrFts) {
                    db.execSQL(
                        "CREATE VIRTUAL TABLE page_ocr_fts USING fts4(pageId, tokens)"
                    )
                }
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS page_ocr_fts
                    USING fts4(pageId, tokens)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE documents ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_documents_syncStatus ON documents(syncStatus)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS documents_fts
                    USING fts4(docId, title, ocrText)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO documents_fts(docId, title, ocrText)
                    SELECT d.id, d.title,
                           COALESCE((SELECT GROUP_CONCAT(p.ocrText, ' ')
                                     FROM pages p
                                     WHERE p.documentId = d.id
                                       AND p.ocrText IS NOT NULL), '')
                    FROM documents d
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS folders (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_createdAt ON folders(createdAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS documents (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        folderId TEXT,
                        pageCount INTEGER NOT NULL,
                        colorTag INTEGER,
                        FOREIGN KEY(folderId) REFERENCES folders(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_createdAt ON documents(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_folderId ON documents(folderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_updatedAt ON documents(updatedAt)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pages (
                        id TEXT NOT NULL PRIMARY KEY,
                        documentId TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        imagePath TEXT NOT NULL,
                        thumbPath TEXT NOT NULL,
                        ocrText TEXT,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        filter TEXT NOT NULL DEFAULT 'original',
                        FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pages_documentId ON pages(documentId)")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_pages_documentId_pageIndex
                    ON pages(documentId, pageIndex)
                    """.trimIndent()
                )
            }
        }
    }
}
