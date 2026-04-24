package app.paperkeep.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P2.1 data model audit — DocumentEntity, PageEntity, FolderEntity, PageOcrEntity + DocumentDao.
 *
 * Covers all fields added/renamed in schema v5:
 *  - DocumentEntity: isFavorite, isArchived, docType
 *  - PageEntity: encryptedImagePath, encryptedThumbPath, ocrStatus, ocrLanguage
 *  - FolderEntity: icon, autoRule
 *  - PageOcrEntity: full round-trip + cascade delete
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DocumentDaoTest {

    private lateinit var db: PaperkeepDatabase
    private lateinit var dao: DocumentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PaperkeepDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.documentDao()
    }

    @After
    fun tearDown() = db.close()

    // ── P2.1: DocumentEntity new fields ──────────────────────────────────────

    @Test
    fun insertDocument_defaultNewFields_areFalseOrNull() = runTest {
        dao.insertDocument(buildDoc("doc-defaults"))
        val result = dao.getDocumentById("doc-defaults")
        assertNotNull(result)
        assertEquals(false, result!!.isFavorite)
        assertEquals(false, result.isArchived)
        assertNull(result.docType)
    }

    @Test
    fun setFavorite_true_persistsCorrectly() = runTest {
        dao.insertDocument(buildDoc("doc-fav"))
        dao.setFavorite("doc-fav", true)
        assertEquals(true, dao.getDocumentById("doc-fav")?.isFavorite)
    }

    @Test
    fun setFavorite_toggle_roundTrips() = runTest {
        dao.insertDocument(buildDoc("doc-fav2"))
        dao.setFavorite("doc-fav2", true)
        dao.setFavorite("doc-fav2", false)
        assertEquals(false, dao.getDocumentById("doc-fav2")?.isFavorite)
    }

    @Test
    fun setArchived_true_persistsCorrectly() = runTest {
        dao.insertDocument(buildDoc("doc-arch"))
        dao.setArchived("doc-arch", true)
        assertEquals(true, dao.getDocumentById("doc-arch")?.isArchived)
    }

    @Test
    fun setDocType_persistsCorrectly() = runTest {
        dao.insertDocument(buildDoc("doc-type"))
        dao.setDocType("doc-type", "receipt")
        assertEquals("receipt", dao.getDocumentById("doc-type")?.docType)
    }

    @Test
    fun setDocType_clearToNull_persistsCorrectly() = runTest {
        dao.insertDocument(buildDoc("doc-type2", docType = "id"))
        dao.setDocType("doc-type2", null)
        assertNull(dao.getDocumentById("doc-type2")?.docType)
    }

    // ── P2.1: observeFavorites / observeArchived ──────────────────────────────

    @Test
    fun observeFavorites_returnsOnlyFavouritedDocuments() = runTest {
        dao.insertDocument(buildDoc("fav-1", isFavorite = true))
        dao.insertDocument(buildDoc("fav-2", isFavorite = false))
        dao.insertDocument(buildDoc("fav-3", isFavorite = true))

        val favs = dao.observeFavorites().first()
        assertEquals(2, favs.size)
        assertTrue(favs.all { it.document.isFavorite })
    }

    @Test
    fun observeArchived_returnsOnlyArchivedDocuments() = runTest {
        dao.insertDocument(buildDoc("arch-1", isArchived = true))
        dao.insertDocument(buildDoc("arch-2", isArchived = false))

        val archived = dao.observeArchived().first()
        assertEquals(1, archived.size)
        assertEquals("arch-1", archived[0].document.id)
    }

    @Test
    fun observeAllWithPages_excludesArchivedByDefault() = runTest {
        dao.insertDocument(buildDoc("live-doc"))
        dao.insertDocument(buildDoc("arch-doc", isArchived = true))

        val all = dao.observeAllWithPages().first()
        assertEquals(1, all.size)
        assertEquals("live-doc", all[0].document.id)
    }

    @Test
    fun observeByDocType_returnsMatchingDocuments() = runTest {
        dao.insertDocument(buildDoc("receipt-1", docType = "receipt"))
        dao.insertDocument(buildDoc("id-1", docType = "id"))
        dao.insertDocument(buildDoc("a4-1", docType = "a4"))

        val receipts = dao.observeByDocType("receipt").first()
        assertEquals(1, receipts.size)
        assertEquals("receipt-1", receipts[0].document.id)
    }

    // ── P2.1: PageEntity renamed fields ──────────────────────────────────────

    @Test
    fun insertPage_encryptedPaths_roundTrip() = runTest {
        dao.insertDocument(buildDoc("doc-paths"))
        val page = buildPage("pg-paths", "doc-paths", 0)
        dao.insertPage(page)

        val result = dao.getPageById("pg-paths")
        assertNotNull(result)
        assertEquals("/enc/pg-paths.enc", result!!.encryptedImagePath)
        assertEquals("/enc/pg-paths.thumb.enc", result.encryptedThumbPath)
    }

    @Test
    fun insertPage_ocrStatusDefault_isPending() = runTest {
        dao.insertDocument(buildDoc("doc-ocr-default"))
        dao.insertPage(buildPage("pg-ocr-default", "doc-ocr-default", 0))

        assertEquals(OcrStatus.PENDING, dao.getPageById("pg-ocr-default")?.ocrStatus)
    }

    @Test
    fun updateOcrStatus_setsDoneAndLanguage() = runTest {
        dao.insertDocument(buildDoc("doc-ocr-status"))
        dao.insertPage(buildPage("pg-ocr-status", "doc-ocr-status", 0))
        dao.updateOcrStatus("pg-ocr-status", OcrStatus.DONE, "en")

        val page = dao.getPageById("pg-ocr-status")
        assertEquals(OcrStatus.DONE, page?.ocrStatus)
        assertEquals("en", page?.ocrLanguage)
    }

    @Test
    fun updateOcrStatus_failed_noLanguage() = runTest {
        dao.insertDocument(buildDoc("doc-ocr-fail"))
        dao.insertPage(buildPage("pg-ocr-fail", "doc-ocr-fail", 0))
        dao.updateOcrStatus("pg-ocr-fail", OcrStatus.FAILED, null)

        val page = dao.getPageById("pg-ocr-fail")
        assertEquals(OcrStatus.FAILED, page?.ocrStatus)
        assertNull(page?.ocrLanguage)
    }

    @Test
    fun updateOcrText_persistsText() = runTest {
        dao.insertDocument(buildDoc("doc-ocr-text"))
        dao.insertPage(buildPage("pg-ocr-text", "doc-ocr-text", 0))
        dao.updateOcrText("pg-ocr-text", "Hello World")

        assertEquals("Hello World", dao.getPageById("pg-ocr-text")?.ocrText)
    }

    @Test
    fun getPendingOcrPages_returnsOnlyPending() = runTest {
        dao.insertDocument(buildDoc("doc-pending"))
        dao.insertPage(buildPage("pg-pending-1", "doc-pending", 0, ocrStatus = OcrStatus.PENDING))
        dao.insertPage(buildPage("pg-pending-2", "doc-pending", 1, ocrStatus = OcrStatus.DONE))
        dao.insertPage(buildPage("pg-pending-3", "doc-pending", 2, ocrStatus = OcrStatus.PENDING))

        val pending = dao.getPendingOcrPages()
        assertEquals(2, pending.size)
        assertTrue(pending.all { it.ocrStatus == OcrStatus.PENDING })
    }

    // ── P2.1: FolderEntity new fields ────────────────────────────────────────

    @Test
    fun insertFolder_iconAndAutoRule_roundTrip() = runTest {
        val folder = buildFolder("f-icon", "Receipts", icon = "receipt", autoRule = "docType=receipt")
        dao.insertFolder(folder)

        val result = dao.getFolderById("f-icon")
        assertNotNull(result)
        assertEquals("receipt", result!!.icon)
        assertEquals("docType=receipt", result.autoRule)
    }

    @Test
    fun insertFolder_defaultIcon_isFolder() = runTest {
        dao.insertFolder(buildFolder("f-default", "Work"))
        assertEquals("folder", dao.getFolderById("f-default")?.icon)
    }

    @Test
    fun insertFolder_nullAutoRule_roundTrips() = runTest {
        dao.insertFolder(buildFolder("f-no-rule", "Personal", autoRule = null))
        assertNull(dao.getFolderById("f-no-rule")?.autoRule)
    }

    // ── P2.1: PageOcrEntity full round-trip + cascade delete ─────────────────

    @Test
    fun insertPageOcr_andGetByPageId_returnsSameBlob() = runTest {
        dao.insertDocument(buildDoc("doc-ocr-entity"))
        dao.insertPage(buildPage("pg-ocr-entity", "doc-ocr-entity", 0))

        val blob = "encrypted-ocr-data".toByteArray()
        dao.insertPageOcr(PageOcrEntity(pageId = "pg-ocr-entity", encryptedText = blob))

        val result = dao.getPageOcr("pg-ocr-entity")
        assertNotNull(result)
        assertTrue(blob.contentEquals(result!!.encryptedText))
    }

    @Test
    fun insertPageOcr_replace_updatesBlob() = runTest {
        dao.insertDocument(buildDoc("doc-ocr-update"))
        dao.insertPage(buildPage("pg-ocr-update", "doc-ocr-update", 0))

        dao.insertPageOcr(PageOcrEntity("pg-ocr-update", "first".toByteArray()))
        dao.insertPageOcr(PageOcrEntity("pg-ocr-update", "second".toByteArray()))

        val result = dao.getPageOcr("pg-ocr-update")
        assertTrue("second".toByteArray().contentEquals(result!!.encryptedText))
    }

    @Test
    fun deletePageOcr_removesRow() = runTest {
        dao.insertDocument(buildDoc("doc-ocr-del"))
        dao.insertPage(buildPage("pg-ocr-del", "doc-ocr-del", 0))
        dao.insertPageOcr(PageOcrEntity("pg-ocr-del", "data".toByteArray()))

        dao.deletePageOcr("pg-ocr-del")
        assertNull(dao.getPageOcr("pg-ocr-del"))
    }

    @Test
    fun deletePageEntity_cascadesDeleteToPageOcr() = runTest {
        dao.insertDocument(buildDoc("doc-ocr-cascade"))
        dao.insertPage(buildPage("pg-ocr-cascade", "doc-ocr-cascade", 0))
        dao.insertPageOcr(PageOcrEntity("pg-ocr-cascade", "data".toByteArray()))

        dao.deletePageById("pg-ocr-cascade")
        assertNull(dao.getPageOcr("pg-ocr-cascade"))
    }

    @Test
    fun getPageOcr_nonExistentPage_returnsNull() = runTest {
        assertNull(dao.getPageOcr("does-not-exist"))
    }

    // ── Existing tests updated for renamed fields ─────────────────────────────

    @Test
    fun insertDocument_andGetById_returnsSameEntity() = runTest {
        val doc = buildDoc(id = "doc-1", title = "Invoice April")
        dao.insertDocument(doc)
        val result = dao.getDocumentById("doc-1")
        assertNotNull(result)
        assertEquals(doc, result)
    }

    @Test
    fun insertPage_andGetById_returnsSamePage() = runTest {
        dao.insertDocument(buildDoc("doc-a"))
        val page = buildPage(id = "pg-1", documentId = "doc-a", pageIndex = 0)
        dao.insertPage(page)
        assertEquals(page, dao.getPageById("pg-1"))
    }

    @Test
    fun getPagesForDocument_returnsPagesInOrder() = runTest {
        dao.insertDocument(buildDoc("doc-b"))
        dao.insertPage(buildPage("p2", "doc-b", 1))
        dao.insertPage(buildPage("p1", "doc-b", 0))
        dao.insertPage(buildPage("p3", "doc-b", 2))

        val pages = dao.getPagesForDocument("doc-b")
        assertEquals(3, pages.size)
        assertEquals(listOf(0, 1, 2), pages.map { it.pageIndex })
    }

    @Test
    fun deleteDocument_cascadesDeleteToPages() = runTest {
        val doc = buildDoc("doc-cascade")
        dao.insertDocument(doc)
        dao.insertPage(buildPage("pg-c1", "doc-cascade", 0))
        dao.insertPage(buildPage("pg-c2", "doc-cascade", 1))
        assertEquals(2, dao.countPages("doc-cascade"))

        dao.deleteDocument(doc)
        assertEquals(0, dao.countPages("doc-cascade"))
    }

    @Test
    fun insertDocument_unicodeTitle_roundTrips() = runTest {
        val title = "文書スキャン — ক্যামেরা — العربية — 🗒️"
        dao.insertDocument(buildDoc("uni-1", title = title))
        assertEquals(title, dao.getDocumentById("uni-1")?.title)
    }

    @Test
    fun deleteFolder_setsDocumentFolderIdToNull() = runTest {
        val folder = buildFolder("f3", "Projects")
        dao.insertFolder(folder)
        dao.insertDocument(buildDoc("doc-in-folder", folderId = "f3"))

        dao.clearFolderReference("f3")
        dao.deleteFolder(folder)

        assertNull(dao.getDocumentById("doc-in-folder")?.folderId)
    }

    @Test
    fun observeAllSortedByTitle_returnsAlphaOrder() = runTest {
        dao.insertDocument(buildDoc("z-doc", "Zebra"))
        dao.insertDocument(buildDoc("a-doc", "Apple"))
        dao.insertDocument(buildDoc("m-doc", "Mango"))

        val sorted = dao.observeAllSortedByTitle().first()
        assertEquals(listOf("Apple", "Mango", "Zebra"), sorted.map { it.document.title })
    }

    @Test
    fun insertDocument_colorTag_roundTrips() = runTest {
        val color = 0xFF_E53935.toInt()
        dao.insertDocument(buildDoc("with-color", colorTag = color))
        assertEquals(color, dao.getDocumentById("with-color")?.colorTag)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildDoc(
        id: String,
        title: String = "Document $id",
        folderId: String? = null,
        pageCount: Int = 1,
        createdAt: Long = System.currentTimeMillis(),
        colorTag: Int? = null,
        docType: String? = null,
        isFavorite: Boolean = false,
        isArchived: Boolean = false,
    ) = DocumentEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = createdAt,
        folderId = folderId,
        pageCount = pageCount,
        colorTag = colorTag,
        docType = docType,
        isFavorite = isFavorite,
        isArchived = isArchived,
    )

    private fun buildPage(
        id: String,
        documentId: String,
        pageIndex: Int,
        ocrText: String? = null,
        ocrStatus: String = OcrStatus.PENDING,
        ocrLanguage: String? = null,
        filter: String = "original",
    ) = PageEntity(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        encryptedImagePath = "/enc/$id.enc",
        encryptedThumbPath = "/enc/$id.thumb.enc",
        ocrStatus = ocrStatus,
        ocrLanguage = ocrLanguage,
        ocrText = ocrText,
        width = 2560,
        height = 1920,
        filter = filter,
    )

    private fun buildFolder(
        id: String,
        name: String,
        icon: String = "folder",
        autoRule: String? = null,
        createdAt: Long = System.currentTimeMillis(),
    ) = FolderEntity(
        id = id,
        name = name,
        icon = icon,
        autoRule = autoRule,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
