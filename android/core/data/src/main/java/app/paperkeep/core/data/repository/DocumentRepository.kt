package app.paperkeep.core.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import app.paperkeep.core.data.db.DocumentDao
import app.paperkeep.core.data.db.DocumentWithPages
import app.paperkeep.core.data.db.toDomain
import app.paperkeep.core.data.db.toEntity
import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.DocumentSort
import app.paperkeep.core.domain.model.Folder
import app.paperkeep.core.domain.model.Page
import app.paperkeep.core.domain.model.PageOcr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val dao: DocumentDao,
) {

    // ── Documents ──────────────────────────────────────────────────────────────

    fun observeDocuments(sort: DocumentSort): Flow<List<Document>> = when (sort) {
        DocumentSort.NEWEST -> dao.observeAllWithPages()
        DocumentSort.OLDEST -> dao.observeAllSortedOldest()
        DocumentSort.TITLE_AZ -> dao.observeAllSortedByTitle()
        DocumentSort.MOST_PAGES -> dao.observeAllSortedByPageCount()
    }.map { list -> list.map { it.toDomain() } }

    fun observeByFolder(folderId: String): Flow<List<Document>> =
        dao.observeByFolder(folderId).map { list -> list.map { it.toDomain() } }

    fun observeFavorites(): Flow<List<Document>> =
        dao.observeFavorites().map { list -> list.map { it.toDomain() } }

    fun observeArchived(): Flow<List<Document>> =
        dao.observeArchived().map { list -> list.map { it.toDomain() } }

    fun observeByDocType(docType: String): Flow<List<Document>> =
        dao.observeByDocType(docType).map { list -> list.map { it.toDomain() } }

    fun observeFolders(): Flow<List<Folder>> =
        dao.observeFolders().map { list -> list.map { it.toDomain() } }

    suspend fun getDocumentById(id: String): Document? =
        dao.getDocumentWithPagesById(id)?.toDomain()

    suspend fun saveDocument(document: Document) {
        dao.insertDocument(document.toEntity())
    }

    suspend fun updateDocument(document: Document) {
        dao.updateDocument(document.toEntity())
    }

    suspend fun deleteDocumentById(id: String) {
        dao.deleteDocumentById(id)
    }

    suspend fun setFavorite(documentId: String, favorite: Boolean) {
        dao.setFavorite(documentId, favorite)
    }

    suspend fun setArchived(documentId: String, archived: Boolean) {
        dao.setArchived(documentId, archived)
    }

    suspend fun setDocType(documentId: String, docType: String?) {
        dao.setDocType(documentId, docType)
    }

    suspend fun updateTitle(documentId: String, title: String) {
        dao.updateTitle(documentId, title, System.currentTimeMillis())
        refreshFtsRow(documentId)
    }

    suspend fun moveDocumentToFolder(documentId: String, folderId: String?) {
        val entity = dao.getDocumentById(documentId) ?: return
        dao.updateDocument(entity.copy(folderId = folderId))
    }

    // ── Pages ──────────────────────────────────────────────────────────────────

    suspend fun savePage(page: Page) {
        dao.insertPage(page.toEntity())
    }

    suspend fun savePages(pages: List<Page>) {
        dao.insertPages(pages.map { it.toEntity() })
    }

    suspend fun updateOcrStatus(pageId: String, status: String, language: String?) {
        dao.updateOcrStatus(pageId, status, language)
    }

    suspend fun updateOcrText(pageId: String, text: String?) {
        dao.updateOcrText(pageId, text)
    }

    suspend fun getPendingOcrPages(limit: Int = 20): List<Page> =
        dao.getPendingOcrPages(limit).map { it.toDomain() }

    // ── PageOcr ────────────────────────────────────────────────────────────────

    suspend fun savePageOcr(ocr: PageOcr) {
        dao.insertPageOcr(ocr.toEntity())
    }

    suspend fun getPageOcr(pageId: String): PageOcr? =
        dao.getPageOcr(pageId)?.toDomain()

    // ── Folders ────────────────────────────────────────────────────────────────

    suspend fun createFolder(folder: Folder) {
        dao.insertFolder(folder.toEntity())
    }

    suspend fun updateFolder(folder: Folder) {
        dao.updateFolder(folder.toEntity())
    }

    suspend fun deleteFolder(folder: Folder) {
        dao.clearFolderReference(folder.id)
        dao.deleteFolder(folder.toEntity())
    }

    // ── Full-text search (FTS4) ────────────────────────────────────────────────

    /**
     * Full-text search across document titles and OCR text.
     *
     * [query] is sanitised before forwarding to FTS4 MATCH (see [sanitiseFtsQuery]).
     * Returns empty list for blank queries rather than returning all documents.
     */
    suspend fun searchDocuments(query: String): List<Document> {
        val sanitised = sanitiseFtsQuery(query)
        if (sanitised.isBlank()) return emptyList()
        val sql = SimpleSQLiteQuery(
            """
            SELECT d.* FROM documents d
            INNER JOIN documents_fts ON documents_fts.docId = d.id
            WHERE documents_fts MATCH ?
            ORDER BY d.createdAt DESC
            """.trimIndent(),
            arrayOf<Any>(sanitised),
        )
        return dao.searchDocumentsRaw(sql).map { it.toDomain() }
    }

    /**
     * Rebuild the FTS row for a single document after its title or pages change.
     * Call after any write that affects searchable content.
     */
    suspend fun refreshFtsRow(documentId: String) {
        val sql = SimpleSQLiteQuery(
            """
            INSERT OR REPLACE INTO documents_fts(docId, title, ocrText)
            SELECT d.id,
                   d.title,
                   COALESCE((SELECT GROUP_CONCAT(p.ocrText, ' ')
                             FROM pages p
                             WHERE p.documentId = d.id
                               AND p.ocrText IS NOT NULL), '')
            FROM documents d
            WHERE d.id = ?
            """.trimIndent(),
            arrayOf<Any>(documentId),
        )
        dao.updateFtsRowRaw(sql)
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    /**
     * Convert a user-supplied query string into a safe FTS4 MATCH expression.
     *
     *  1. Strip characters that are not alphanumeric, space, or hyphen.
     *  2. Split on whitespace → each word becomes a prefix term ("word*").
     *  3. Join with space. Drop tokens shorter than 3 chars (FTS minimum).
     *
     * Example: "  hello  world  " → "hello* world*"
     */
    internal fun sanitiseFtsQuery(raw: String): String =
        raw.replace(Regex("[^\\w\\s\\-]"), "")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .joinToString(" ") { "$it*" }
}
