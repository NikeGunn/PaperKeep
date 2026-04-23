package app.paperkeep.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One-level-deep folder that groups [DocumentEntity] records.
 *
 * Deleting a folder moves its documents to root (folderId = null); it does NOT
 * cascade-delete the documents themselves.
 */
@Entity(
    tableName = "folders",
    indices = [Index("createdAt")],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Epoch millis */
    val createdAt: Long,
    /** Epoch millis */
    val updatedAt: Long,
)
