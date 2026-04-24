package app.paperkeep.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Encrypted OCR result for a single page.
 *
 * Stored separately from [PageEntity] so:
 *  1. The main page query stays lean (never loads large OCR blobs for list views).
 *  2. The FTS4 table ([documents_fts]) can be populated from the plaintext at
 *     write time and thereafter only ever exposes HMAC'd tokens (see §6.4).
 *
 * [encryptedText] — AES-256-GCM ciphertext of a JSON envelope:
 *   { "text": "...", "boxes": [{"text": "...", "x": 0, "y": 0, "w": 100, "h": 20}, ...] }
 *
 * The JSON is encrypted with the same K_master from Android Keystore that
 * protects page images. IV is the first 12 bytes of [encryptedText].
 */
@Entity(
    tableName = "page_ocr",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PageOcrEntity(
    @PrimaryKey val pageId: String,
    /** AES-256-GCM ciphertext of plaintext + bounding-boxes JSON. First 12 bytes = IV. */
    val encryptedText: ByteArray,
) {
    // ByteArray requires manual equals/hashCode to preserve value semantics in data classes.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PageOcrEntity) return false
        return pageId == other.pageId && encryptedText.contentEquals(other.encryptedText)
    }

    override fun hashCode(): Int = 31 * pageId.hashCode() + encryptedText.contentHashCode()
}
