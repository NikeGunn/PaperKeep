package com.scanvault.feature.reader

import com.scanvault.core.data.repository.DocumentRepository
import com.scanvault.core.domain.model.Document
import com.scanvault.core.domain.model.Page
import io.mockk.coEvery
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
        imagePath = "/images/page$index.enc",
        thumbPath = "/thumbs/page$index.enc",
        ocrText = ocrText,
        width = 1080,
        height = 1920,
        filter = "original",
    )

    private fun fakeDocument(pages: List<Page>) = Document(
        id = "doc-1",
        title = "Test Document",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        folderId = null,
        pageCount = pages.size,
        colorTag = null,
        pages = pages,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        viewModel = ReaderViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pages loaded correctly for document`() = runTest {
        val pages = listOf(fakePage(0, "Hello"), fakePage(1, "World"))
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(pages)

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        assertEquals(2, viewModel.pages.value.size)
        assertEquals("page-0", viewModel.pages.value[0].id)
        assertEquals("page-1", viewModel.pages.value[1].id)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `pages sorted by pageIndex after load`() = runTest {
        val pages = listOf(fakePage(1), fakePage(0), fakePage(2))
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(pages)

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        val loaded = viewModel.pages.value
        assertEquals(0, loaded[0].pageIndex)
        assertEquals(1, loaded[1].pageIndex)
        assertEquals(2, loaded[2].pageIndex)
    }

    @Test
    fun `empty pages returned when document not found`() = runTest {
        coEvery { repository.getDocumentById("missing") } returns null

        viewModel.loadDocument("missing")
        advanceUntilIdle()

        assertTrue(viewModel.pages.value.isEmpty())
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `OCR overlay toggle starts false then toggles`() = runTest {
        assertFalse(viewModel.ocrOverlayEnabled.value)

        viewModel.toggleOcrOverlay()
        assertTrue(viewModel.ocrOverlayEnabled.value)

        viewModel.toggleOcrOverlay()
        assertFalse(viewModel.ocrOverlayEnabled.value)
    }

    @Test
    fun `zoom level clamped between MIN and MAX`() {
        viewModel.onZoomChanged(0.1f)
        assertEquals(ReaderViewModel.MIN_ZOOM, viewModel.zoomLevel.value)

        viewModel.onZoomChanged(10f)
        assertEquals(ReaderViewModel.MAX_ZOOM, viewModel.zoomLevel.value)

        viewModel.onZoomChanged(2.5f)
        assertEquals(2.5f, viewModel.zoomLevel.value)
    }

    @Test
    fun `currentPage clamped to valid range`() = runTest {
        val pages = listOf(fakePage(0), fakePage(1), fakePage(2))
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(pages)

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        viewModel.onPageChanged(5)
        assertEquals(2, viewModel.currentPage.value)

        viewModel.onPageChanged(-1)
        assertEquals(0, viewModel.currentPage.value)
    }

    @Test
    fun `isLoading true during load then false after`() = runTest {
        val pages = listOf(fakePage(0))
        coEvery { repository.getDocumentById("doc-1") } returns fakeDocument(pages)

        // Before load starts, loading should be true (initial state)
        assertTrue(viewModel.isLoading.value)

        viewModel.loadDocument("doc-1")
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `FLAG_SECURE test placeholder - window flag check delegated to instrumented test`() {
        // FLAG_SECURE is set in a SideEffect inside the Composable, which requires
        // an Activity window. This is verified in androidTest (instrumented).
        // Here we document the constant for reference.
        assertEquals(0x2000, android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
}
