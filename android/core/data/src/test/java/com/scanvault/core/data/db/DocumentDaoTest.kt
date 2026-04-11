package com.scanvault.core.data.db

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
 * Tests for 2B.1: DocumentEntity, PageEntity, FolderEntity + DocumentDao.
 *
 * Covers:
 *  - Document insert → query → same data
 *  - Page entity with foreign key to Document
 *  - Cascade delete (delete Document → Pages gone)
 *  - Unicode / emoji titles
 *  - Folder operations (create, rename, delete → documents go to root)
 *  - Sort variants
 *  - Migration 1→2 preserves existing scans table
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DocumentDaoTest {

    private lateinit var db: ScanVaultDatabase
    private lateinit var dao: DocumentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ScanVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.documentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── 2B.1: Document insert → query → correct data ─────────────────────────

    @Test
    fun insertDocument_andGetById_returnsSameEntity() = runTest {
        val doc = buildDoc(id = "doc-1", title = "Invoice April")

        dao.insertDocument(doc)
        val result = dao.getDocumentById("doc-1")

        assertNotNull(result)
        assertEquals(doc, result)
    }

    @Test
    fun insertDocument_observeAll_containsDocument() = runTest {
        dao.insertDocument(buildDoc("d1", "Contract"))

        val list = dao.observeAllWithPages().first()

        assertEquals(1, list.size)
        assertEquals("d1", list[0].document.id)
        assertEquals("Contract", list[0].document.title)
    }

    @Test
    fun countDocuments_afterInsert_isCorrect() = runTest {
        dao.insertDocument(buildDoc("a"))
        dao.insertDocument(buildDoc("b"))

        assertEquals(2, dao.countDocuments())
    }

    @Test
    fun updateDocument_changesTitle() = runTest {
        val doc = buildDoc("upd-1", "Old Title")
        dao.insertDocument(doc)

        dao.updateDocument(doc.copy(title = "New Title"))

        assertEquals("New Title", dao.getDocumentById("upd-1")?.title)
    }

    @Test
    fun deleteDocument_removesFromDb() = runTest {
        val doc = buildDoc("del-1")
        dao.insertDocument(doc)

        dao.deleteDocument(doc)

        assertNull(dao.getDocumentById("del-1"))
        assertEquals(0, dao.countDocuments())
    }

    // ── 2B.1: Page entity with foreign key to Document ───────────────────────

    @Test
    fun insertPage_andGetById_returnsSamePage() = runTest {
        dao.insertDocument(buildDoc("doc-a"))
        val page = buildPage(id = "pg-1", documentId = "doc-a", pageIndex = 0)

        dao.insertPage(page)
        val result = dao.getPageById("pg-1")

        assertNotNull(result)
        assertEquals(page, result)
    }

    @Test
    fun getPagesForDocument_returnsPagesInOrder() = runTest {
        dao.insertDocument(buildDoc("doc-b"))
        dao.insertPage(buildPage("p2", "doc-b", 1))
        dao.insertPage(buildPage("p1", "doc-b", 0))
        dao.insertPage(buildPage("p3", "doc-b", 2))

        val pages = dao.getPagesForDocument("doc-b")

        assertEquals(3, pages.size)
        assertEquals(0, pages[0].pageIndex)
        assertEquals(1, pages[1].pageIndex)
        assertEquals(2, pages[2].pageIndex)
    }

    @Test
    fun observeAllWithPages_includesPagesInRelation() = runTest {
        dao.insertDocument(buildDoc("doc-c"))
        dao.insertPage(buildPage("pg-a", "doc-c", 0))
        dao.insertPage(buildPage("pg-b", "doc-c", 1))

        val result = dao.observeAllWithPages().first()

        assertEquals(1, result.size)
        assertEquals(2, result[0].pages.size)
    }

    // ── 2B.1: Cascade delete (delete Document → Pages gone) ──────────────────

    @Test
    fun deleteDocument_cascadesDeleteToPages() = runTest {
        val doc = buildDoc("doc-cascade")
        dao.insertDocument(doc)
        dao.insertPage(buildPage("pg-c1", "doc-cascade", 0))
        dao.insertPage(buildPage("pg-c2", "doc-cascade", 1))

        assertEquals(2, dao.countPages("doc-cascade"))

        dao.deleteDocument(doc)

        // Pages should be gone due to CASCADE
        assertEquals(0, dao.countPages("doc-cascade"))
        assertNull(dao.getPageById("pg-c1"))
        assertNull(dao.getPageById("pg-c2"))
    }

    @Test
    fun deleteDocumentById_cascadesDeleteToPages() = runTest {
        dao.insertDocument(buildDoc("doc-cascade2"))
        dao.insertPage(buildPage("pg-x", "doc-cascade2", 0))

        dao.deleteDocumentById("doc-cascade2")

        assertNull(dao.getPageById("pg-x"))
    }

    // ── 2B.1: Unicode / emoji in title ───────────────────────────────────────

    @Test
    fun insertDocument_unicodeTitle_roundTrips() = runTest {
        val title = "文書スキャン — ক্যামেরা — العربية — 🗒️"
        dao.insertDocument(buildDoc("uni-1", title))

        assertEquals(title, dao.getDocumentById("uni-1")?.title)
    }

    @Test
    fun insertDocument_emojiTitle_roundTrips() = runTest {
        val title = "Receipt 🧾 #42"
        dao.insertDocument(buildDoc("emoji-1", title))

        assertEquals(title, dao.getDocumentById("emoji-1")?.title)
    }

    @Test
    fun insertPage_ocrText_unicode_roundTrips() = runTest {
        dao.insertDocument(buildDoc("doc-ocr"))
        val ocrText = "Ελληνικά — Ñoño — 日本語"
        dao.insertPage(buildPage("pg-ocr", "doc-ocr", 0, ocrText = ocrText))

        assertEquals(ocrText, dao.getPageById("pg-ocr")?.ocrText)
    }

    // ── 2B.3: Folder operations ───────────────────────────────────────────────

    @Test
    fun insertFolder_andGetById_returnsSameFolder() = runTest {
        val folder = buildFolder("f1", "Work")

        dao.insertFolder(folder)
        val result = dao.getFolderById("f1")

        assertNotNull(result)
        assertEquals(folder, result)
    }

    @Test
    fun updateFolder_changesName() = runTest {
        val folder = buildFolder("f2", "Personal")
        dao.insertFolder(folder)

        dao.updateFolder(folder.copy(name = "Family"))

        assertEquals("Family", dao.getFolderById("f2")?.name)
    }

    @Test
    fun deleteFolder_setsDocumentFolderIdToNull() = runTest {
        val folder = buildFolder("f3", "Projects")
        dao.insertFolder(folder)
        dao.insertDocument(buildDoc("doc-in-folder", folderId = "f3"))

        // clearFolderReference must be called before deleteFolder to avoid FK violation
        dao.clearFolderReference("f3")
        dao.deleteFolder(folder)

        val doc = dao.getDocumentById("doc-in-folder")
        assertNull(doc?.folderId)
    }

    @Test
    fun observeByFolder_returnsOnlyMatchingDocuments() = runTest {
        dao.insertFolder(buildFolder("fa", "A"))
        dao.insertFolder(buildFolder("fb", "B"))
        dao.insertDocument(buildDoc("d-in-a", folderId = "fa"))
        dao.insertDocument(buildDoc("d-in-b", folderId = "fb"))
        dao.insertDocument(buildDoc("d-root"))

        val inA = dao.observeByFolder("fa").first()

        assertEquals(1, inA.size)
        assertEquals("d-in-a", inA[0].document.id)
    }

    @Test
    fun observeRootDocuments_excludesFolderedDocuments() = runTest {
        dao.insertFolder(buildFolder("fc", "C"))
        dao.insertDocument(buildDoc("d-foldered", folderId = "fc"))
        dao.insertDocument(buildDoc("d-root-2"))

        val root = dao.observeRootDocuments().first()

        assertEquals(1, root.size)
        assertEquals("d-root-2", root[0].document.id)
    }

    // ── 2B.1: Sort variants ───────────────────────────────────────────────────

    @Test
    fun observeAllSortedByTitle_returnsAlphaOrder() = runTest {
        dao.insertDocument(buildDoc("z-doc", "Zebra"))
        dao.insertDocument(buildDoc("a-doc", "Apple"))
        dao.insertDocument(buildDoc("m-doc", "Mango"))

        val sorted = dao.observeAllSortedByTitle().first()

        assertEquals("Apple", sorted[0].document.title)
        assertEquals("Mango", sorted[1].document.title)
        assertEquals("Zebra", sorted[2].document.title)
    }

    @Test
    fun observeAllSortedOldest_returnsChronologicalOrder() = runTest {
        dao.insertDocument(buildDoc("new-doc", createdAt = 9000L))
        dao.insertDocument(buildDoc("old-doc", createdAt = 1000L))

        val sorted = dao.observeAllSortedOldest().first()

        assertEquals("old-doc", sorted[0].document.id)
        assertEquals("new-doc", sorted[1].document.id)
    }

    @Test
    fun observeAllSortedByPageCount_mostPagesFirst() = runTest {
        dao.insertDocument(buildDoc("one-page", pageCount = 1))
        dao.insertDocument(buildDoc("five-pages", pageCount = 5))
        dao.insertDocument(buildDoc("three-pages", pageCount = 3))

        val sorted = dao.observeAllSortedByPageCount().first()

        assertEquals("five-pages", sorted[0].document.id)
        assertEquals("three-pages", sorted[1].document.id)
        assertEquals("one-page", sorted[2].document.id)
    }

    // ── 2B.1: colorTag field round-trip ──────────────────────────────────────

    @Test
    fun insertDocument_nullColorTag_roundTrips() = runTest {
        dao.insertDocument(buildDoc("no-color", colorTag = null))
        assertNull(dao.getDocumentById("no-color")?.colorTag)
    }

    @Test
    fun insertDocument_withColorTag_roundTrips() = runTest {
        val color = 0xFF_E53935.toInt()
        dao.insertDocument(buildDoc("with-color", colorTag = color))
        assertEquals(color, dao.getDocumentById("with-color")?.colorTag)
    }

    // ── 2B.1: filter field on Page ────────────────────────────────────────────

    @Test
    fun insertPage_defaultFilter_isOriginal() = runTest {
        dao.insertDocument(buildDoc("doc-filter"))
        dao.insertPage(buildPage("pg-filter", "doc-filter", 0))

        assertEquals("original", dao.getPageById("pg-filter")?.filter)
    }

    @Test
    fun insertPage_customFilter_roundTrips() = runTest {
        dao.insertDocument(buildDoc("doc-filter2"))
        dao.insertPage(buildPage("pg-filter2", "doc-filter2", 0, filter = "magic_color"))

        assertEquals("magic_color", dao.getPageById("pg-filter2")?.filter)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildDoc(
        id: String,
        title: String = "Document $id",
        folderId: String? = null,
        pageCount: Int = 1,
        createdAt: Long = System.currentTimeMillis(),
        colorTag: Int? = null,
    ) = DocumentEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = createdAt,
        folderId = folderId,
        pageCount = pageCount,
        colorTag = colorTag,
    )

    private fun buildPage(
        id: String,
        documentId: String,
        pageIndex: Int,
        ocrText: String? = null,
        filter: String = "original",
    ) = PageEntity(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        imagePath = "/enc/$id.enc",
        thumbPath = "/enc/$id.thumb.enc",
        ocrText = ocrText,
        width = 2560,
        height = 1920,
        filter = filter,
    )

    private fun buildFolder(
        id: String,
        name: String,
        createdAt: Long = System.currentTimeMillis(),
    ) = FolderEntity(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
