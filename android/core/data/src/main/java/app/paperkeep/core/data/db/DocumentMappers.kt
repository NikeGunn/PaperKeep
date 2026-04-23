package app.paperkeep.core.data.db

import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.Folder
import app.paperkeep.core.domain.model.Page
import app.paperkeep.core.domain.model.SyncStatus
import java.time.Instant

fun DocumentWithPages.toDomain() = Document(
    id = document.id,
    title = document.title,
    createdAt = Instant.ofEpochMilli(document.createdAt),
    updatedAt = Instant.ofEpochMilli(document.updatedAt),
    folderId = document.folderId,
    pageCount = document.pageCount,
    colorTag = document.colorTag,
    pages = pages.map { it.toDomain() },
    syncStatus = document.syncStatus,
)

fun PageEntity.toDomain() = Page(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    imagePath = imagePath,
    thumbPath = thumbPath,
    ocrText = ocrText,
    width = width,
    height = height,
    filter = filter,
)

fun FolderEntity.toDomain() = Folder(
    id = id,
    name = name,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Document.toEntity() = DocumentEntity(
    id = id,
    title = title,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    folderId = folderId,
    pageCount = pageCount,
    colorTag = colorTag,
    syncStatus = syncStatus,
)

fun Folder.toEntity() = FolderEntity(
    id = id,
    name = name,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)
