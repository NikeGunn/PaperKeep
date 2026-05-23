package app.paperkeep.feature.reader.reorder

import app.paperkeep.core.data.repository.DocumentRepository
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
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderReorderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: DocumentRepository = mockk(relaxed = true)
    private lateinit var viewModel: ReaderReorderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ReaderReorderViewModel(repository)
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun pages(ids: List<String>): List<Page> = ids.mapIndexed { idx, id ->
        Page(
            id = id,
            documentId = "doc",
            pageIndex = idx,
            encryptedImagePath = "",
            encryptedThumbPath = "",
            ocrStatus = "pending",
            ocrLanguage = null,
            ocrText = null,
            width = 100,
            height = 100,
            filter = "original",
            title = null,
        )
    }

    private fun document(pages: List<Page>): Document = Document(
        id = "doc",
        title = "t",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        folderId = null,
        pageCount = pages.size,
        colorTag = null,
        docType = null,
        isFavorite = false,
        isArchived = false,
        pages = pages,
    )

    @Test
    fun load_populatesPagesInOrder() = runTest {
        coEvery { repository.getDocumentById("doc") } returns document(pages(listOf("a", "b", "c")))
        viewModel.load("doc")
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), viewModel.pages.value.map { it.id })
    }

    @Test
    fun moveTo_swapsAdjacent() = runTest {
        coEvery { repository.getDocumentById("doc") } returns document(pages(listOf("a", "b", "c")))
        viewModel.load("doc")
        advanceUntilIdle()
        viewModel.moveTo(0, 1)
        assertEquals(listOf("b", "a", "c"), viewModel.pages.value.map { it.id })
    }

    @Test
    fun moveTo_outOfRangeClampsToEdges() = runTest {
        coEvery { repository.getDocumentById("doc") } returns document(pages(listOf("a", "b", "c")))
        viewModel.load("doc")
        advanceUntilIdle()
        viewModel.moveTo(0, 99) // beyond end → clamps to 2
        assertEquals(listOf("b", "c", "a"), viewModel.pages.value.map { it.id })
    }

    @Test
    fun moveTo_sameIndex_noOp() = runTest {
        coEvery { repository.getDocumentById("doc") } returns document(pages(listOf("a", "b", "c")))
        viewModel.load("doc")
        advanceUntilIdle()
        viewModel.moveTo(1, 1)
        assertEquals(listOf("a", "b", "c"), viewModel.pages.value.map { it.id })
    }

    @Test
    fun save_callsReorderPagesWithCurrentOrder() = runTest {
        coEvery { repository.getDocumentById("doc") } returns document(pages(listOf("a", "b", "c")))
        viewModel.load("doc")
        advanceUntilIdle()
        viewModel.moveTo(2, 0)
        var done = false
        viewModel.save { done = true }
        advanceUntilIdle()
        coVerify { repository.reorderPages("doc", listOf("c", "a", "b")) }
        org.junit.Assert.assertTrue(done)
    }
}
