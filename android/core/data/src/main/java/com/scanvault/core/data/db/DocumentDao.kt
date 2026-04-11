package com.scanvault.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    // ── Documents ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity)

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Delete
    suspend fun deleteDocument(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): DocumentEntity?

    /** All documents newest-first. */
    @Transaction
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAllWithPages(): Flow<List<DocumentWithPages>>

    /** Documents in a specific folder, newest-first. */
    @Transaction
    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun observeByFolder(folderId: String): Flow<List<DocumentWithPages>>

    /** Root-level documents (no folder), newest-first. */
    @Transaction
    @Query("SELECT * FROM documents WHERE folderId IS NULL ORDER BY createdAt DESC")
    fun observeRootDocuments(): Flow<List<DocumentWithPages>>

    /** Sort by title A-Z. */
    @Transaction
    @Query("SELECT * FROM documents ORDER BY title COLLATE NOCASE ASC")
    fun observeAllSortedByTitle(): Flow<List<DocumentWithPages>>

    /** Sort by oldest first. */
    @Transaction
    @Query("SELECT * FROM documents ORDER BY createdAt ASC")
    fun observeAllSortedOldest(): Flow<List<DocumentWithPages>>

    /** Sort by most pages first. */
    @Transaction
    @Query("SELECT * FROM documents ORDER BY pageCount DESC")
    fun observeAllSortedByPageCount(): Flow<List<DocumentWithPages>>

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun countDocuments(): Int

    /** Nullify folderId for all documents in [folderId] before deleting the folder. */
    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolderReference(folderId: String)

    // ── Pages ─────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Delete
    suspend fun deletePage(page: PageEntity)

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPagesForDocument(documentId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPageById(id: String): PageEntity?

    @Query("SELECT COUNT(*) FROM pages WHERE documentId = :documentId")
    suspend fun countPages(documentId: String): Int

    // ── Folders ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    @Query("SELECT * FROM folders ORDER BY createdAt DESC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: String): FolderEntity?

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun countFolders(): Int
}
