package com.scanvault.core.data.db

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Convenience aggregate returned by [DocumentDao] queries that need both
 * the document header and all of its pages in a single load.
 */
data class DocumentWithPages(
    @Embedded val document: DocumentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "documentId",
    )
    val pages: List<PageEntity>,
)
