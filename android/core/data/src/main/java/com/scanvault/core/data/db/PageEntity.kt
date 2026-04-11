package com.scanvault.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single page inside a [DocumentEntity].
 *
 * Cascade-deletes when the parent document is deleted.
 * [filter] is the name of the applied image filter (e.g. "magic_color", "bw", "original").
 */
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("documentId"),
        Index(value = ["documentId", "pageIndex"], unique = true),
    ],
)
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    /** 0-based ordering within the document. */
    val pageIndex: Int,
    /** Absolute path to the encrypted full-size image file (.enc). */
    val imagePath: String,
    /** Absolute path to the encrypted 256 px thumbnail file (.enc). */
    val thumbPath: String,
    /** OCR-extracted plain text, or null if OCR has not run yet. */
    val ocrText: String?,
    /** Width in pixels of the captured page. */
    val width: Int,
    /** Height in pixels of the captured page. */
    val height: Int,
    /** Applied image filter name. Defaults to "original". */
    val filter: String = "original",
)
