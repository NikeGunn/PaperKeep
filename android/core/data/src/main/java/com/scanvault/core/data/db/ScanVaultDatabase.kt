package com.scanvault.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ScanEntity::class,
        FolderEntity::class,
        DocumentEntity::class,
        PageEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ScanVaultDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun documentDao(): DocumentDao

    companion object {
        /**
         * Migration 2 → 3: adds the FTS4 virtual table for full-text search over
         * document titles and OCR text.
         *
         * The FTS content table is backed by [DocumentEntity]; the initial rowid
         * population copies existing data so search works immediately after upgrade.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create standalone FTS4 virtual table. This is NOT a content table
                // (no content= parameter) so Room won't try to sync it automatically.
                // The repository layer calls updateFtsRow() after every write.
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS documents_fts
                    USING fts4(
                        docId,
                        title,
                        ocrText
                    )
                    """.trimIndent()
                )

                // Populate FTS index with existing documents + their OCR text.
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
         * Migration 1 → 2: adds folders, documents, and pages tables.
         * The existing `scans` table is left untouched for backwards compatibility.
         */
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
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_folders_createdAt ON folders(createdAt)"
                )

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
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_documents_createdAt ON documents(createdAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_documents_folderId ON documents(folderId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_documents_updatedAt ON documents(updatedAt)"
                )

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
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pages_documentId ON pages(documentId)"
                )
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
