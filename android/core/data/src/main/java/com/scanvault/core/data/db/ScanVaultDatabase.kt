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
    version = 2,
    exportSchema = true,
)
abstract class ScanVaultDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun documentDao(): DocumentDao

    companion object {
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
