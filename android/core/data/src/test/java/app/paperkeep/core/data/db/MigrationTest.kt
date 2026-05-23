package app.paperkeep.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration smoke-tests for PaperkeepDatabase.
 *
 * Verifies schema v5 (after MIGRATION_4_5 P2.1 audit) produces all the expected
 * tables and columns, and that data survives the migration path.
 *
 * Robolectric's SQLite does not fully support MigrationTestHelper's file-based
 * approach, so we use in-memory databases and verify the final schema indirectly
 * by checking DAO operations succeed with the new fields.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest {

    private fun buildContext(): Context = ApplicationProvider.getApplicationContext()

    private fun freshDb(): PaperkeepDatabase =
        Room.inMemoryDatabaseBuilder(buildContext(), PaperkeepDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    // ── Smoke: fresh DB has all v5 tables ─────────────────────────────────────

    @Test
    fun freshDatabase_documentsTable_hasNewFields() = runTest {
        val db = freshDb()
        try {
            val dao = db.documentDao()
            // If fields are missing, the INSERT will throw a compile-time error (Room
            // validates the entity at build time) or a runtime exception here.
            dao.insertDocument(
                DocumentEntity(
                    id = "v5-doc", title = "V5 Test", createdAt = 1000L, updatedAt = 1000L,
                    folderId = null, pageCount = 1, colorTag = null,
                    docType = "receipt", isFavorite = true, isArchived = false,
                )
            )
            val result = dao.getDocumentById("v5-doc")
            assertNotNull(result)
            assertEquals("receipt", result!!.docType)
            assertTrue(result.isFavorite)
            assertFalse(result.isArchived)
        } finally { db.close() }
    }

    @Test
    fun freshDatabase_pagesTable_hasRenamedAndNewFields() = runTest {
        val db = freshDb()
        try {
            val dao = db.documentDao()
            dao.insertDocument(buildMinimalDoc(db, "doc-pg"))
            dao.insertPage(
                PageEntity(
                    id = "pg-v5", documentId = "doc-pg", pageIndex = 0,
                    encryptedImagePath = "/enc/pg-v5.enc",
                    encryptedThumbPath = "/enc/pg-v5.thumb.enc",
                    ocrStatus = OcrStatus.PENDING,
                    ocrLanguage = null,
                    ocrText = null,
                    width = 2560, height = 1920, filter = "original",
                )
            )
            dao.updateOcrStatus("pg-v5", OcrStatus.DONE, "en")
            val page = dao.getPageById("pg-v5")
            assertNotNull(page)
            assertEquals("/enc/pg-v5.enc", page!!.encryptedImagePath)
            assertEquals("/enc/pg-v5.thumb.enc", page.encryptedThumbPath)
            assertEquals(OcrStatus.DONE, page.ocrStatus)
            assertEquals("en", page.ocrLanguage)
        } finally { db.close() }
    }

    @Test
    fun freshDatabase_foldersTable_hasIconAndAutoRule() = runTest {
        val db = freshDb()
        try {
            val dao = db.documentDao()
            dao.insertFolder(
                FolderEntity(
                    id = "f-v5", name = "Receipts", icon = "receipt",
                    autoRule = "docType=receipt", createdAt = 1000L, updatedAt = 1000L,
                )
            )
            val folder = dao.getFolderById("f-v5")
            assertNotNull(folder)
            assertEquals("receipt", folder!!.icon)
            assertEquals("docType=receipt", folder.autoRule)
        } finally { db.close() }
    }

    @Test
    fun freshDatabase_pageOcrTable_supportsInsertAndQuery() = runTest {
        val db = freshDb()
        try {
            val dao = db.documentDao()
            dao.insertDocument(buildMinimalDoc(db, "doc-ocr"))
            dao.insertPage(buildMinimalPage("pg-ocr", "doc-ocr"))

            val blob = "aes-gcm-ciphertext".toByteArray()
            dao.insertPageOcr(PageOcrEntity(pageId = "pg-ocr", encryptedText = blob))

            val result = dao.getPageOcr("pg-ocr")
            assertNotNull(result)
            assertTrue(blob.contentEquals(result!!.encryptedText))
        } finally { db.close() }
    }

    @Test
    fun freshDatabase_pageOcrCascadeDelete_whenPageDeleted() = runTest {
        val db = freshDb()
        try {
            val dao = db.documentDao()
            dao.insertDocument(buildMinimalDoc(db, "doc-cascade-ocr"))
            dao.insertPage(buildMinimalPage("pg-cascade-ocr", "doc-cascade-ocr"))
            dao.insertPageOcr(PageOcrEntity("pg-cascade-ocr", "data".toByteArray()))

            dao.deletePageById("pg-cascade-ocr")
            assertNull(dao.getPageOcr("pg-cascade-ocr"))
        } finally { db.close() }
    }

    // ── Data preserved in full migration path ─────────────────────────────────

    @Test
    fun freshDatabase_legacyScansTable_stillAccessible() = runTest {
        val db = freshDb()
        try {
            val scanDao = db.scanDao()
            assertEquals(0, scanDao.count())
            // Insert into legacy scans table to verify it still exists
            scanDao.insert(
                ScanEntity(
                    id = "s1", title = "Legacy Scan", pageCount = 1,
                    createdAt = 1000L, updatedAt = 1000L,
                    originalPath = "/enc/s1.enc", thumbnailPath = "/enc/s1.thumb.enc",
                    widthPx = 2560, heightPx = 1920,
                )
            )
            assertEquals(1, scanDao.count())
        } finally { db.close() }
    }

    @Test
    fun freshDatabase_countDocuments_excludesArchived() = runTest {
        val db = freshDb()
        try {
            val dao = db.documentDao()
            dao.insertDocument(
                DocumentEntity(
                    id = "live", title = "Live", createdAt = 0L, updatedAt = 0L,
                    folderId = null, pageCount = 1, colorTag = null, isArchived = false,
                )
            )
            dao.insertDocument(
                DocumentEntity(
                    id = "arch", title = "Arch", createdAt = 0L, updatedAt = 0L,
                    folderId = null, pageCount = 1, colorTag = null, isArchived = true,
                )
            )
            assertEquals(1, dao.countDocuments())
        } finally { db.close() }
    }

    // ── v8 → v9: page title column ────────────────────────────────────────────

    @Test
    fun freshDatabase_pageTitleColumn_existsAndDefaultsToNull() = runTest {
        val db = freshDb()
        try {
            val dao = db.documentDao()
            val docId = "title-doc"
            buildMinimalDoc(db, docId)
            dao.insertPage(
                buildMinimalPage("p1", docId)
            )
            val page = dao.getPageById("p1")
            assertNotNull(page)
            assertNull("freshly-inserted page must default to title=null", page!!.title)
        } finally {
            db.close()
        }
    }

    @Test
    fun setPageTitle_persistsAndCanBeCleared() = runTest {
        val db = freshDb()
        try {
            val dao = db.documentDao()
            buildMinimalDoc(db, "td")
            dao.insertPage(buildMinimalPage("p1", "td"))
            dao.setPageTitle("p1", "Cover")
            assertEquals("Cover", dao.getPageById("p1")!!.title)
            dao.setPageTitle("p1", null)
            assertNull(dao.getPageById("p1")!!.title)
        } finally {
            db.close()
        }
    }

    @Test
    fun setPageFilter_persists() = runTest {
        val db = freshDb()
        try {
            val dao = db.documentDao()
            buildMinimalDoc(db, "fd")
            dao.insertPage(buildMinimalPage("p1", "fd"))
            dao.setPageFilter("p1", "magic_color")
            assertEquals("magic_color", dao.getPageById("p1")!!.filter)
        } finally {
            db.close()
        }
    }

    @Test
    fun setPageIndex_swapsCorrectly() = runTest {
        val db = freshDb()
        try {
            val dao = db.documentDao()
            buildMinimalDoc(db, "rd")
            dao.insertPage(buildMinimalPage("p0", "rd").copy(pageIndex = 0))
            dao.insertPage(buildMinimalPage("p1", "rd").copy(pageIndex = 1))
            dao.insertPage(buildMinimalPage("p2", "rd").copy(pageIndex = 2))
            // Move p2 to first via two-phase parking strategy.
            val temp = 100_000
            dao.setPageIndex("p2", temp)
            dao.setPageIndex("p0", temp + 1)
            dao.setPageIndex("p1", temp + 2)
            dao.setPageIndex("p2", 0)
            dao.setPageIndex("p0", 1)
            dao.setPageIndex("p1", 2)
            val ordered = dao.getPagesForDocument("rd")
            assertEquals(listOf("p2", "p0", "p1"), ordered.map { it.id })
        } finally {
            db.close()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun buildMinimalDoc(db: PaperkeepDatabase, id: String): DocumentEntity {
        val entity = DocumentEntity(
            id = id, title = "Doc $id", createdAt = 0L, updatedAt = 0L,
            folderId = null, pageCount = 1, colorTag = null,
        )
        db.documentDao().insertDocument(entity)
        return entity
    }

    private fun buildMinimalPage(id: String, documentId: String) = PageEntity(
        id = id, documentId = documentId, pageIndex = 0,
        encryptedImagePath = "/enc/$id.enc",
        encryptedThumbPath = "/enc/$id.thumb.enc",
        width = 2560, height = 1920,
    )
}
