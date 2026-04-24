package app.paperkeep.core.domain.model

import java.time.Instant

/** Sort options available in the library screen. */
enum class DocumentSort {
    NEWEST,
    OLDEST,
    TITLE_AZ,
    MOST_PAGES,
}

/**
 * Domain model representing a scanned document with all its pages.
 *
 * [docType] mirrors the TFLite classifier output:
 *   "receipt" | "id" | "a4" | "whiteboard" | "book" | "business_card" | null (unclassified)
 */
data class Document(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val folderId: String?,
    val pageCount: Int,
    val colorTag: Int?,
    val docType: String?,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val pages: List<Page>,
)

/** Domain model for a single page inside a [Document]. */
data class Page(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    /** Absolute path to the AES-256-GCM encrypted full-size image (.enc). */
    val encryptedImagePath: String,
    /** Absolute path to the AES-256-GCM encrypted thumbnail (.enc). */
    val encryptedThumbPath: String,
    /** "pending" | "done" | "failed". See OcrStatus constants. */
    val ocrStatus: String,
    /** BCP-47 language tag detected by ML Kit, e.g. "en", "hi". Null until done. */
    val ocrLanguage: String?,
    /** OCR plaintext for FTS. Null until ocrStatus == "done". */
    val ocrText: String?,
    val width: Int,
    val height: Int,
    /** "original" | "magic_color" | "grayscale" | "bw" | "auto". */
    val filter: String,
)

/** Domain model for a user-created folder. System folders are derived, not stored. */
data class Folder(
    val id: String,
    val name: String,
    /** Material icon name or emoji key. */
    val icon: String,
    /** Auto-rule DSL, e.g. "docType=receipt". Null = no rule. */
    val autoRule: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Encrypted OCR result returned from the repository for a specific page. */
data class PageOcr(
    val pageId: String,
    /** AES-256-GCM ciphertext; IV is the first 12 bytes. */
    val encryptedText: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PageOcr) return false
        return pageId == other.pageId && encryptedText.contentEquals(other.encryptedText)
    }

    override fun hashCode(): Int = 31 * pageId.hashCode() + encryptedText.contentHashCode()
}
