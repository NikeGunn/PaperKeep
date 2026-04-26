package app.paperkeep.feature.reader

import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.data.repository.DocumentRepository
import app.paperkeep.core.data.share.SharePayloadBuilder
import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.Page
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: DocumentRepository
    private lateinit var viewModel: ReaderViewModel

    private fun fakePage(index: Int, ocrText: String? = null) = Page(
        id = "page-$index",
        documentId = "doc-1",
        pageIndex = index,
        encryptedImagePath = "/images/page$index.enc",
        encryptedThumbPath = "/thumbs/page$index.enc",
        ocrStatus = "done",
        ocrLanguage = null,
        ocrText = ocrText,
        width = 1080,
        height = 1920,
        filter = "original",
    )

    private fun fakeDocument(
        id: String = "doc-1",
        title: String = "Test Document",
        pages: List<Page> = emptyList(),
    ) = Document(
        id = id,
        title = title,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        folderId = null,
        pageCount = pages.size,
        colorTag = null,
        docType = null,
        isFavorite = false,
        isArchived = false,
        pages = pages,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        val sharePayloadBuilder = mockk<SharePayloadBuilder>(relaxed = true)
        val dispatchers = object : AppDispatchers {
            override val main = testDispatcher
            override val io = testDispatcher
            override val default = testDispatcher
        }
        viewModel = ReaderViewModel(repository, sharePayloadBuilder, dispatchers)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    @Test
    fun `pages loaded correctly for document`() = runTest {
        val pages = listOf(fakePage(0, "Hello"), fakePage(1, "World"))
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(pages = pages)

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        assertEquals(2, viewModel.pages.value.size)
        assertEquals("page-0", viewModel.pages.value[0].id)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `pages sorted by pageIndex after load`() = runTest {
        val pages = listOf(fakePage(2), fakePage(0), fakePage(1))
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(pages = pages)

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        assertEquals(0, viewModel.pages.value[0].pageIndex)
        assertEquals(1, viewModel.pages.value[1].pageIndex)
        assertEquals(2, viewModel.pages.value[2].pageIndex)
    }

    @Test
    fun `empty pages when document not found`() = runTest {
        coEvery { repository.getDocumentById("missing") } returns null

        viewModel.loadDocument("missing")
        advanceUntilIdle()

        assertTrue(viewModel.pages.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `documentTitle set from loaded document`() = runTest {
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(title = "My Scan")

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        assertEquals("My Scan", viewModel.documentTitle.value)
    }

    // ── OCR overlay ──────────────────────────────────────────────────────────

    @Test
    fun `ocrOverlayEnabled starts false then toggles`() = runTest {
        assertFalse(viewModel.ocrOverlayEnabled.value)
        viewModel.toggleOcrOverlay()
        assertTrue(viewModel.ocrOverlayEnabled.value)
        viewModel.toggleOcrOverlay()
        assertFalse(viewModel.ocrOverlayEnabled.value)
    }

    // ── Bottom bar visibility ─────────────────────────────────────────────────

    @Test
    fun `showBottomBar starts true then toggles`() {
        assertTrue(viewModel.showBottomBar.value)
        viewModel.toggleBottomBar()
        assertFalse(viewModel.showBottomBar.value)
        viewModel.toggleBottomBar()
        assertTrue(viewModel.showBottomBar.value)
    }

    // ── Zoom ─────────────────────────────────────────────────────────────────

    @Test
    fun `zoom clamped between MIN and MAX`() {
        viewModel.onZoomChanged(0.1f)
        assertEquals(ReaderViewModel.MIN_ZOOM, viewModel.zoomLevel.value)

        viewModel.onZoomChanged(10f)
        assertEquals(ReaderViewModel.MAX_ZOOM, viewModel.zoomLevel.value)

        viewModel.onZoomChanged(2.5f)
        assertEquals(2.5f, viewModel.zoomLevel.value)
    }

    // ── Page navigation ───────────────────────────────────────────────────────

    @Test
    fun `currentPage clamped to valid range`() = runTest {
        val pages = listOf(fakePage(0), fakePage(1), fakePage(2))
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(pages = pages)

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        viewModel.onPageChanged(99)
        assertEquals(2, viewModel.currentPage.value)

        viewModel.onPageChanged(-1)
        assertEquals(0, viewModel.currentPage.value)
    }

    // ── Rename ────────────────────────────────────────────────────────────────

    @Test
    fun `startRename sets isRenaming to true`() {
        viewModel.startRename()
        assertTrue(viewModel.isRenaming.value)
    }

    @Test
    fun `cancelRename clears isRenaming and restores original title`() = runTest {
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(title = "Original")

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        viewModel.startRename()
        viewModel.onTitleChanged("Edited")
        viewModel.cancelRename()

        assertFalse(viewModel.isRenaming.value)
        assertEquals("Original", viewModel.documentTitle.value)
    }

    @Test
    fun `commitRename calls repository updateTitle`() = runTest {
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(title = "Old")

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        viewModel.startRename()
        viewModel.onTitleChanged("New Title")
        viewModel.commitRename()
        advanceUntilIdle()

        coVerify { repository.updateTitle("doc-1", "New Title") }
        assertFalse(viewModel.isRenaming.value)
    }

    @Test
    fun `commitRename with blank title cancels rename`() = runTest {
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(title = "Original")

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        viewModel.startRename()
        viewModel.onTitleChanged("   ")
        viewModel.commitRename()

        assertFalse(viewModel.isRenaming.value)
        assertEquals("Original", viewModel.documentTitle.value)
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    fun `deleteDocument emits DocumentDeleted event`() = runTest {
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument()

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        viewModel.deleteDocument()
        advanceUntilIdle()

        assertEquals(ReaderEvent.DocumentDeleted, viewModel.event.value)
        coVerify { repository.deleteDocumentById("doc-1") }
    }

    @Test
    fun `consumeEvent clears the event`() = runTest {
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument()

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        viewModel.deleteDocument()
        advanceUntilIdle()

        assertNotNull(viewModel.event.value)
        viewModel.consumeEvent()
        assertNull(viewModel.event.value)
    }

    // ── Format-sheet flow ─────────────────────────────────────────────────────

    @Test
    fun `openShareSheet sets format sheet to SHARE when pages loaded`() = runTest {
        val pages = listOf(fakePage(0))
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(pages = pages)

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        viewModel.openShareSheet()
        assertEquals(ReaderViewModel.FormatSheetMode.SHARE, viewModel.formatSheetMode.value)
    }

    @Test
    fun `openShareSheet does nothing when no pages`() {
        viewModel.openShareSheet()
        assertNull(viewModel.formatSheetMode.value)
    }

    @Test
    fun `openDownloadSheet sets format sheet to DOWNLOAD`() = runTest {
        val pages = listOf(fakePage(0))
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(pages = pages)

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        viewModel.openDownloadSheet()
        assertEquals(ReaderViewModel.FormatSheetMode.DOWNLOAD, viewModel.formatSheetMode.value)
    }

    @Test
    fun `dismissFormatSheet clears mode`() = runTest {
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(pages = listOf(fakePage(0)))
        viewModel.loadDocument("doc-1")
        advanceUntilIdle()
        viewModel.openShareSheet()
        viewModel.dismissFormatSheet()
        assertNull(viewModel.formatSheetMode.value)
    }

    // ── FLAG_SECURE constant ──────────────────────────────────────────────────

    @Test
    fun `FLAG_SECURE constant has correct value`() {
        assertEquals(0x2000, android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
}
