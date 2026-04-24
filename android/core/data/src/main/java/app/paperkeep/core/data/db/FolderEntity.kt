package app.paperkeep.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One-level-deep folder for grouping [DocumentEntity] records.
 *
 * System folders ("All", "Favorites", "Archive") are NOT stored here — they
 * are derived from [DocumentEntity.isFavorite] / [DocumentEntity.isArchived].
 * Only user-created folders live in this table.
 *
 * [icon] — Material icon name or emoji key used as the folder's visual badge.
 *          Defaults to "folder" (Material icon). Never null.
 *
 * [autoRule] — DSL expression for automatic filing. Null = no auto-rule.
 *              Format (Phase 4): "docType=receipt" | "docType=id" | null.
 *              Rule wiring is Phase 4 (P4.7); the field is reserved here.
 */
@Entity(
    tableName = "folders",
    indices = [Index("createdAt")],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Material icon name or emoji key. Never null; defaults to "folder". */
    val icon: String = "folder",
    /** Auto-rule DSL expression, e.g. "docType=receipt". Null = no rule. */
    val autoRule: String? = null,
    /** Epoch millis. */
    val createdAt: Long,
    /** Epoch millis. */
    val updatedAt: Long,
)
