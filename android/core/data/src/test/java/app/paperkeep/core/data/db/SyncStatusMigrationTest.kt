package app.paperkeep.core.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P2.1 schema-v5 field contract tests.
 *
 * SyncStatus (cloud-backend concept) was removed in schema v5.
 * This file now verifies the OcrStatus constants and the default-field
 * contracts for the entities updated in P2.1.
 *
 * These are pure JVM tests — no Room or Android dependencies.
 */
class P21SchemaContractTest {

    // ── OcrStatus constants ───────────────────────────────────────────────────

    @Test
    fun ocrStatus_pending_isCorrectString() {
        assertEquals("pending", OcrStatus.PENDING)
    }

    @Test
    fun ocrStatus_done_isCorrectString() {
        assertEquals("done", OcrStatus.DONE)
    }

    @Test
    fun ocrStatus_failed_isCorrectString() {
        assertEquals("failed", OcrStatus.FAILED)
    }

    @Test
    fun ocrStatus_allValuesDistinct() {
        val statuses = setOf(OcrStatus.PENDING, OcrStatus.DONE, OcrStatus.FAILED)
        assertEquals(3, statuses.size)
    }

    // ── DocumentEntity default fields ─────────────────────────────────────────

    @Test
    fun documentEntity_defaultIsFavorite_isFalse() {
        val entity = buildMinimalDoc()
        assertFalse(entity.isFavorite)
    }

    @Test
    fun documentEntity_defaultIsArchived_isFalse() {
        val entity = buildMinimalDoc()
        assertFalse(entity.isArchived)
    }

    @Test
    fun documentEntity_defaultDocType_isNull() {
        val entity = buildMinimalDoc()
        assertNull(entity.docType)
    }

    @Test
    fun documentEntity_withAllNewFields_roundTrips() {
        val entity = DocumentEntity(
            id = "test", title = "Test", createdAt = 0L, updatedAt = 0L,
            folderId = null, pageCount = 1, colorTag = null,
            docType = "receipt", isFavorite = true, isArchived = false,
        )
        assertEquals("receipt", entity.docType)
        assertEquals(true, entity.isFavorite)
        assertEquals(false, entity.isArchived)
    }

    // ── PageEntity renamed + new fields ───────────────────────────────────────

    @Test
    fun pageEntity_defaultOcrStatus_isPending() {
        val page = buildMinimalPage()
        assertEquals(OcrStatus.PENDING, page.ocrStatus)
    }

    @Test
    fun pageEntity_defaultOcrLanguage_isNull() {
        val page = buildMinimalPage()
        assertNull(page.ocrLanguage)
    }

    @Test
    fun pageEntity_encryptedImagePath_fieldName_isCorrect() {
        val page = buildMinimalPage(imagePath = "/enc/test.enc")
        assertEquals("/enc/test.enc", page.encryptedImagePath)
    }

    @Test
    fun pageEntity_encryptedThumbPath_fieldName_isCorrect() {
        val page = buildMinimalPage(thumbPath = "/enc/test.thumb.enc")
        assertEquals("/enc/test.thumb.enc", page.encryptedThumbPath)
    }

    // ── FolderEntity new fields ───────────────────────────────────────────────

    @Test
    fun folderEntity_defaultIcon_isFolder() {
        val folder = FolderEntity("f1", "Work", createdAt = 0L, updatedAt = 0L)
        assertEquals("folder", folder.icon)
    }

    @Test
    fun folderEntity_defaultAutoRule_isNull() {
        val folder = FolderEntity("f2", "Receipts", createdAt = 0L, updatedAt = 0L)
        assertNull(folder.autoRule)
    }

    @Test
    fun folderEntity_withIconAndAutoRule_roundTrips() {
        val folder = FolderEntity(
            id = "f3", name = "Receipts", icon = "receipt",
            autoRule = "docType=receipt", createdAt = 0L, updatedAt = 0L,
        )
        assertEquals("receipt", folder.icon)
        assertEquals("docType=receipt", folder.autoRule)
    }

    // ── PageOcrEntity equals/hashCode contract ───────────────────────────────

    @Test
    fun pageOcrEntity_equality_basedOnContent() {
        val blob = byteArrayOf(1, 2, 3)
        val a = PageOcrEntity("page-1", blob)
        val b = PageOcrEntity("page-1", blob.copyOf())
        assertEquals(a, b)
    }

    @Test
    fun pageOcrEntity_differentBlobs_notEqual() {
        val a = PageOcrEntity("page-1", byteArrayOf(1, 2, 3))
        val b = PageOcrEntity("page-1", byteArrayOf(9, 9, 9))
        assertFalse(a == b)
    }

    @Test
    fun pageOcrEntity_differentPageId_notEqual() {
        val blob = byteArrayOf(1, 2, 3)
        val a = PageOcrEntity("page-1", blob)
        val b = PageOcrEntity("page-2", blob.copyOf())
        assertFalse(a == b)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildMinimalDoc() = DocumentEntity(
        id = "d", title = "T", createdAt = 0L, updatedAt = 0L,
        folderId = null, pageCount = 1, colorTag = null,
    )

    private fun buildMinimalPage(
        imagePath: String = "/enc/p.enc",
        thumbPath: String = "/enc/p.thumb.enc",
    ) = PageEntity(
        id = "p", documentId = "d", pageIndex = 0,
        encryptedImagePath = imagePath,
        encryptedThumbPath = thumbPath,
        width = 1920, height = 2560,
    )
}
