package app.paperkeep.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single page inside a [DocumentEntity].
 *
 * Paths point to AES-256-GCM encrypted `.enc` files in `filesDir/scans/`.
 * The `ocr*` fields track ML Kit pipeline status — see [OcrStatus].
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
        Index("ocrStatus"),
    ],
)
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    /** 0-based ordering within the document. */
    val pageIndex: Int,
    /** Absolute path to the AES-256-GCM encrypted full-size image file (.enc). */
    val encryptedImagePath: String,
    /** Absolute path to the AES-256-GCM encrypted 256 px thumbnail file (.enc). */
    val encryptedThumbPath: String,
    /** OCR pipeline status. See [OcrStatus]. Default: PENDING until ML Kit runs. */
    val ocrStatus: String = OcrStatus.PENDING,
    /** BCP-47 language tag detected by ML Kit (e.g. "en", "hi"). Null until OCR completes. */
    val ocrLanguage: String? = null,
    /** OCR plain text stored inline for FTS. Null until OCR completes successfully. */
    val ocrText: String? = null,
    /** Width in pixels of the captured (perspective-corrected) page. */
    val width: Int,
    /** Height in pixels of the captured page. */
    val height: Int,
    /** Applied image filter. One of: "original" | "magic_color" | "grayscale" | "bw" | "auto". */
    val filter: String = "original",
    /** Optional per-page title (e.g. "Cover", "Signature"). Null = no title. */
    val title: String? = null,
)

/** OCR pipeline status values stored as TEXT in the database. */
object OcrStatus {
    const val PENDING = "pending"
    const val DONE = "done"
    const val FAILED = "failed"
}
