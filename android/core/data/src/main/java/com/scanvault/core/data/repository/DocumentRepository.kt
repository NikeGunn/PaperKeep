package com.scanvault.core.data.repository

import com.scanvault.core.data.db.DocumentDao
import com.scanvault.core.data.db.FolderEntity
import com.scanvault.core.data.db.toDomain
import com.scanvault.core.data.db.toEntity
import com.scanvault.core.domain.model.Document
import com.scanvault.core.domain.model.DocumentSort
import com.scanvault.core.domain.model.Folder
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
            com.scanvault.core.data.db.DocumentWithPages(entity, pages).toDomain()
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

    suspend fun moveDocumentToFolder(documentId: String, folderId: String?) {
        val entity = dao.getDocumentById(documentId) ?: return
        dao.updateDocument(entity.copy(folderId = folderId))
    }
}
