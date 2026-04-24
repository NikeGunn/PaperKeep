package app.paperkeep.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A scanned document — one entity may have many [PageEntity] rows.
 *
 * [folderId] null → document lives at the root level ("All" system folder).
 * Deleting a folder sets child documents' folderId to null (SET_NULL, never CASCADE).
 *
 * [docType] values: "receipt" | "id" | "a4" | "whiteboard" | "book" | "business_card" | null
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
        Index("isFavorite"),
        Index("isArchived"),
        Index("docType"),
    ],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** Epoch millis. */
    val createdAt: Long,
    /** Epoch millis. */
    val updatedAt: Long,
    /** Null = root level ("All"). */
    val folderId: String?,
    val pageCount: Int,
    /** Optional ARGB colour tag (e.g. 0xFF_E53935.toInt()). Null = no tag. */
    val colorTag: Int?,
    /** Document type from TFLite classifier. Null = not yet classified. */
    val docType: String? = null,
    /** True if the user has starred/favourited this document. */
    val isFavorite: Boolean = false,
    /** True if moved to the Archive system folder. */
    val isArchived: Boolean = false,
)
