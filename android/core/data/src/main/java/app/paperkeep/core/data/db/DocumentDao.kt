package app.paperkeep.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
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

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentWithPagesById(id: String): DocumentWithPages?

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): DocumentEntity?

    /** All documents, newest-first. */
    @Transaction
    @Query("SELECT * FROM documents WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun observeAllWithPages(): Flow<List<DocumentWithPages>>

    /** Documents in a specific user folder, newest-first. */
    @Transaction
    @Query("SELECT * FROM documents WHERE folderId = :folderId AND isArchived = 0 ORDER BY createdAt DESC")
    fun observeByFolder(folderId: String): Flow<List<DocumentWithPages>>

    /** Favourited documents, newest-first. */
    @Transaction
    @Query("SELECT * FROM documents WHERE isFavorite = 1 AND isArchived = 0 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<DocumentWithPages>>

    /** Archived documents, newest-first. */
    @Transaction
    @Query("SELECT * FROM documents WHERE isArchived = 1 ORDER BY createdAt DESC")
    fun observeArchived(): Flow<List<DocumentWithPages>>

    /** Documents filtered by docType, newest-first. */
    @Transaction
    @Query("SELECT * FROM documents WHERE docType = :docType AND isArchived = 0 ORDER BY createdAt DESC")
    fun observeByDocType(docType: String): Flow<List<DocumentWithPages>>

    /** Sort by title A-Z, excluding archived. */
    @Transaction
    @Query("SELECT * FROM documents WHERE isArchived = 0 ORDER BY title COLLATE NOCASE ASC")
    fun observeAllSortedByTitle(): Flow<List<DocumentWithPages>>

    /** Sort by oldest-first, excluding archived. */
    @Transaction
    @Query("SELECT * FROM documents WHERE isArchived = 0 ORDER BY createdAt ASC")
    fun observeAllSortedOldest(): Flow<List<DocumentWithPages>>

    /** Sort by most pages, excluding archived. */
    @Transaction
    @Query("SELECT * FROM documents WHERE isArchived = 0 ORDER BY pageCount DESC")
    fun observeAllSortedByPageCount(): Flow<List<DocumentWithPages>>

    @Query("SELECT COUNT(*) FROM documents WHERE isArchived = 0")
    suspend fun countDocuments(): Int

    /** Nullify folderId for all documents in a folder before the folder is deleted. */
    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolderReference(folderId: String)

    @Query("UPDATE documents SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE documents SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("UPDATE documents SET docType = :docType WHERE id = :id")
    suspend fun setDocType(id: String, docType: String?)

    @Query("UPDATE documents SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE documents SET pageCount = :count, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePageCount(id: String, count: Int, updatedAt: Long)

    // ── Pages ─────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Delete
    suspend fun deletePage(page: PageEntity)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePageById(id: String)

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPagesForDocument(documentId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPageById(id: String): PageEntity?

    @Query("SELECT COUNT(*) FROM pages WHERE documentId = :documentId")
    suspend fun countPages(documentId: String): Int

    /** Update OCR status and detected language for a page. */
    @Query("UPDATE pages SET ocrStatus = :status, ocrLanguage = :language WHERE id = :id")
    suspend fun updateOcrStatus(id: String, status: String, language: String?)

    /** Update the inline OCR text cache (used by FTS). */
    @Query("UPDATE pages SET ocrText = :text WHERE id = :id")
    suspend fun updateOcrText(id: String, text: String?)

    /** Set or clear the per-page title (Edit toolbar — Page Title tool). */
    @Query("UPDATE pages SET title = :title WHERE id = :id")
    suspend fun setPageTitle(id: String, title: String?)

    /** Update the applied filter key for a page (Edit toolbar — Filter tool). */
    @Query("UPDATE pages SET filter = :filter WHERE id = :id")
    suspend fun setPageFilter(id: String, filter: String)

    /** Update encrypted file paths + dimensions for a page (Crop re-do, Retake). */
    @Query(
        "UPDATE pages SET encryptedImagePath = :imagePath, encryptedThumbPath = :thumbPath, " +
            "width = :width, height = :height WHERE id = :id",
    )
    suspend fun setPagePaths(id: String, imagePath: String, thumbPath: String, width: Int, height: Int)

    /** Update only the page index (Reorder Pages tool). Run inside a transaction. */
    @Query("UPDATE pages SET pageIndex = :pageIndex WHERE id = :id")
    suspend fun setPageIndex(id: String, pageIndex: Int)

    /** Fetch pages pending OCR, ordered by pageIndex. */
    @Query("SELECT * FROM pages WHERE ocrStatus = 'pending' ORDER BY pageIndex ASC LIMIT :limit")
    suspend fun getPendingOcrPages(limit: Int = 20): List<PageEntity>

    // ── PageOcr ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageOcr(ocr: PageOcrEntity)

    @Query("SELECT * FROM page_ocr WHERE pageId = :pageId")
    suspend fun getPageOcr(pageId: String): PageOcrEntity?

    @Query("DELETE FROM page_ocr WHERE pageId = :pageId")
    suspend fun deletePageOcr(pageId: String)

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

    // ── Full-text search (FTS4 — title/OCR plaintext) ────────────────────────
    //
    // These use @RawQuery to bypass Room's compile-time validation for the FTS4
    // virtual table. Always pre-sanitise the FTS expression before calling these
    // (see DocumentRepository.sanitiseFtsQuery — never pass raw user input).

    @Transaction
    @RawQuery(observedEntities = [DocumentEntity::class])
    suspend fun searchDocumentsRaw(query: SimpleSQLiteQuery): List<DocumentWithPages>

    @RawQuery
    suspend fun updateFtsRowRaw(query: SimpleSQLiteQuery): Int

    @RawQuery
    suspend fun rebuildFtsIndexRaw(query: SimpleSQLiteQuery): Int

    // ── HMAC'd OCR FTS4 (page_ocr_fts) ───────────────────────────────────────
    //
    // The page_ocr_fts virtual table stores opaque HMAC tokens per §6.4.
    // Callers must supply pre-HMAC'd token strings — never raw user text.

    /** Upsert the HMAC token row for [pageId]. */
    @RawQuery
    suspend fun upsertOcrFtsRowRaw(query: SimpleSQLiteQuery): Int

    /** Delete the HMAC token row for [pageId] (called when a page is deleted). */
    @RawQuery
    suspend fun deleteOcrFtsRowRaw(query: SimpleSQLiteQuery): Int

    /**
     * Search page_ocr_fts for documents whose OCR tokens MATCH the expression.
     * Returns the matching document rows (via JOIN pages → documents).
     */
    @Transaction
    @RawQuery(observedEntities = [DocumentEntity::class])
    suspend fun searchByOcrFtsRaw(query: SimpleSQLiteQuery): List<DocumentWithPages>
}
