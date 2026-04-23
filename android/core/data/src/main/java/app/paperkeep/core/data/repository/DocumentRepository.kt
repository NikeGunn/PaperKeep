package app.paperkeep.core.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import app.paperkeep.core.data.db.DocumentDao
import app.paperkeep.core.data.db.FolderEntity
import app.paperkeep.core.data.db.toDomain
import app.paperkeep.core.data.db.toEntity
import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.DocumentSort
import app.paperkeep.core.domain.model.Folder
import app.paperkeep.core.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val dao: DocumentDao,
) {
    fun observeDocuments(sort: DocumentSort): Flow<List<Document>> = when (sort) {
        DocumentSort.NEWEST -> dao.observeAllWithPages()
        DocumentSort.OLDEST -> dao.observeAllSortedOldest()
        DocumentSort.TITLE_AZ -> dao.observeAllSortedByTitle()
        DocumentSort.MOST_PAGES -> dao.observeAllSortedByPageCount()
    }.map { list -> list.map { it.toDomain() } }

    fun observeByFolder(folderId: String): Flow<List<Document>> =
        dao.observeByFolder(folderId).map { list -> list.map { it.toDomain() } }

    fun observeRootDocuments(): Flow<List<Document>> =
        dao.observeRootDocuments().map { list -> list.map { it.toDomain() } }

    fun observeFolders(): Flow<List<Folder>> =
        dao.observeFolders().map { list -> list.map { it.toDomain() } }

    suspend fun getDocumentById(id: String): Document? =
        dao.getDocumentById(id)?.let { entity ->
            // Build a DocumentWithPages-like structure by querying pages too
            val pages = dao.getPagesForDocument(id)
            app.paperkeep.core.data.db.DocumentWithPages(entity, pages).toDomain()
        }

    suspend fun saveDocument(document: Document) {
        dao.insertDocument(document.toEntity())
    }

    suspend fun updateDocument(document: Document) {
        dao.updateDocument(document.toEntity())
    }

    suspend fun deleteDocument(document: Document) {
        dao.deleteDocumentById(document.id)
    }

    suspend fun deleteDocumentById(id: String) {
        dao.deleteDocumentById(id)
    }

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

    suspend fun updateSyncStatus(documentId: String, status: SyncStatus) {
        dao.updateSyncStatus(documentId, status)
    }

    suspend fun moveDocumentToFolder(documentId: String, folderId: String?) {
        val entity = dao.getDocumentById(documentId) ?: return
        dao.updateDocument(entity.copy(folderId = folderId))
    }

    /**
     * Full-text search across document titles and OCR text content.
     *
     * The [query] is sanitised before forwarding to FTS4 MATCH:
     * - Non-alphanumeric chars (except spaces and hyphens) are stripped.
     * - Each word becomes a prefix token (e.g. "inv" → "inv*").
     * - Returns empty list for blank queries rather than all documents.
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
     * Rebuild FTS index row for a single document after its title or pages change.
     * Call this after saving/updating a document or its OCR text.
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

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Convert a user-supplied query string into a safe FTS4 MATCH expression.
     *
     * Rules:
     * 1. Strip any character that is not alphanumeric, space, or hyphen.
     * 2. Split on whitespace → each token becomes a prefix term ("token*").
     * 3. Join tokens with " ".
     *
     * Example: "  hello  world  " → "hello* world*"
     */
    internal fun sanitiseFtsQuery(raw: String): String =
        raw.replace(Regex("[^\\w\\s\\-]"), "")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "$it*" }
}
