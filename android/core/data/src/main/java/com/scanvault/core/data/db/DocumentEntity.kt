package com.scanvault.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A scanned document — one entity may have many [PageEntity] rows.
 *
 * [folderId] is nullable; null means the document lives at the root level.
 * Deleting the parent [FolderEntity] sets [folderId] to null via the
 * application layer (see [DocumentDao.clearFolderReference]).
 */
@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("createdAt"),
        Index("folderId"),
        Index("updatedAt"),
    ],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** Epoch millis */
    val createdAt: Long,
    /** Epoch millis */
    val updatedAt: Long,
    /** Nullable — null means root level. */
    val folderId: String?,
    val pageCount: Int,
    /**
     * Optional colour tag for visual organisation.
     * Stored as an ARGB integer (e.g. 0xFF_E53935.toInt()). Null = no tag.
     */
    val colorTag: Int?,
)
