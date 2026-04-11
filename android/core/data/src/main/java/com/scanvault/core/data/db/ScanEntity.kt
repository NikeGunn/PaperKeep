package com.scanvault.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a captured scan.
 *
 * One entity = one document (may have multiple pages).
 * Page files are stored as encrypted `.enc` files referenced by [originalPath].
 */
@Entity(
    tableName = "scans",
    indices = [Index("createdAt")],
)
data class ScanEntity(
    @PrimaryKey val id: String,
    val title: String,
    val pageCount: Int,
    /** Epoch millis */
    val createdAt: Long,
    /** Epoch millis */
    val updatedAt: Long,
    /** Absolute path to the encrypted primary page file. */
    val originalPath: String,
    /** Absolute path to the encrypted 256px thumbnail. */
    val thumbnailPath: String,
    /** Width of the scan in pixels. */
    val widthPx: Int,
    /** Height of the scan in pixels. */
    val heightPx: Int,
)
