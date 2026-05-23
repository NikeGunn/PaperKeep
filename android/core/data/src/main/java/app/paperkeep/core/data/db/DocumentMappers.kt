package app.paperkeep.core.data.db

import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.Folder
import app.paperkeep.core.domain.model.Page
import app.paperkeep.core.domain.model.PageOcr
import java.time.Instant

fun DocumentWithPages.toDomain() = Document(
    id = document.id,
    title = document.title,
    createdAt = Instant.ofEpochMilli(document.createdAt),
    updatedAt = Instant.ofEpochMilli(document.updatedAt),
    folderId = document.folderId,
    pageCount = document.pageCount,
    colorTag = document.colorTag,
    docType = document.docType,
    isFavorite = document.isFavorite,
    isArchived = document.isArchived,
    pages = pages.sortedBy { it.pageIndex }.map { it.toDomain() },
)

fun PageEntity.toDomain() = Page(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    encryptedImagePath = encryptedImagePath,
    encryptedThumbPath = encryptedThumbPath,
    ocrStatus = ocrStatus,
    ocrLanguage = ocrLanguage,
    ocrText = ocrText,
    width = width,
    height = height,
    filter = filter,
    title = title,
)

fun FolderEntity.toDomain() = Folder(
    id = id,
    name = name,
    icon = icon,
    autoRule = autoRule,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun PageOcrEntity.toDomain() = PageOcr(
    pageId = pageId,
    encryptedText = encryptedText,
)

fun Document.toEntity() = DocumentEntity(
    id = id,
    title = title,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    folderId = folderId,
    pageCount = pageCount,
    colorTag = colorTag,
    docType = docType,
    isFavorite = isFavorite,
    isArchived = isArchived,
)

fun Page.toEntity() = PageEntity(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    encryptedImagePath = encryptedImagePath,
    encryptedThumbPath = encryptedThumbPath,
    ocrStatus = ocrStatus,
    ocrLanguage = ocrLanguage,
    ocrText = ocrText,
    width = width,
    height = height,
    filter = filter,
    title = title,
)

fun Folder.toEntity() = FolderEntity(
    id = id,
    name = name,
    icon = icon,
    autoRule = autoRule,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun PageOcr.toEntity() = PageOcrEntity(
    pageId = pageId,
    encryptedText = encryptedText,
)
