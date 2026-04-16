package com.scanvault.core.domain.model

import java.time.Instant

/** Sort options available in the library screen. */
enum class DocumentSort {
    NEWEST,
    OLDEST,
    TITLE_AZ,
    MOST_PAGES,
}

/** Domain model representing a scanned document with all its pages. */
data class Document(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val folderId: String?,
    val pageCount: Int,
    val colorTag: Int?,
    val pages: List<Page>,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
)

/** Domain model for a single page inside a [Document]. */
data class Page(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val imagePath: String,
    val thumbPath: String,
    val ocrText: String?,
    val width: Int,
    val height: Int,
    val filter: String,
)

/** Domain model for a folder. */
data class Folder(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
