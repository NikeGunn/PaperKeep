package app.paperkeep.feature.reader.edit

import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.data.crypto.EncryptedImageStore
import app.paperkeep.core.data.repository.DocumentRepository
import app.paperkeep.core.imaging.ImageFilter
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : AppDispatchers {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
    }
    private val repository: DocumentRepository = mockk(relaxed = true)
    private val imageStore: EncryptedImageStore = mockk(relaxed = true)

    private lateinit var viewModel: ReaderEditViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ReaderEditViewModel(repository, imageStore, dispatchers)
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun toggleEditMode_flipsState() {
        assertFalse(viewModel.editMode.value)
        viewModel.toggleEditMode()
        assertTrue(viewModel.editMode.value)
        viewModel.toggleEditMode()
        assertFalse(viewModel.editMode.value)
    }

    @Test
    fun exitEditMode_alwaysTurnsOff() {
        viewModel.toggleEditMode()
        viewModel.exitEditMode()
        assertFalse(viewModel.editMode.value)
    }

    @Test
    fun onToolPicked_unimplementedTool_emitsComingSoon() {
        viewModel.onToolPicked(EditTool.BRUSH, "doc1", "page1", ImageFilter.ORIGINAL, null)
        val event = viewModel.event.value
        assertTrue(event is ReaderEditViewModel.EditEvent.ComingSoon)
        assertEquals(EditTool.BRUSH, (event as ReaderEditViewModel.EditEvent.ComingSoon).tool)
    }

    @Test
    fun onToolPicked_reorder_emitsOpenReorder() {
        viewModel.onToolPicked(EditTool.REORDER_PAGES, "doc1", "page1", ImageFilter.ORIGINAL, null)
        val event = viewModel.event.value
        assertTrue(event is ReaderEditViewModel.EditEvent.OpenReorder)
        assertEquals("doc1", (event as ReaderEditViewModel.EditEvent.OpenReorder).documentId)
    }

    @Test
    fun onToolPicked_pageTitle_emitsPromptPageTitle() {
        viewModel.onToolPicked(EditTool.PAGE_TITLE, "doc1", "page1", ImageFilter.ORIGINAL, "Cover")
        val event = viewModel.event.value
        assertTrue(event is ReaderEditViewModel.EditEvent.PromptPageTitle)
        val prompt = event as ReaderEditViewModel.EditEvent.PromptPageTitle
        assertEquals("page1", prompt.pageId)
        assertEquals("Cover", prompt.current)
    }

    @Test
    fun onToolPicked_filter_emitsPromptFilter() {
        viewModel.onToolPicked(EditTool.FILTER, "doc1", "page1", ImageFilter.MAGIC_COLOR, null)
        val event = viewModel.event.value
        assertTrue(event is ReaderEditViewModel.EditEvent.PromptFilter)
        assertEquals(ImageFilter.MAGIC_COLOR, (event as ReaderEditViewModel.EditEvent.PromptFilter).current)
    }

    @Test
    fun onToolPicked_retake_emitsOpenRetake() {
        viewModel.onToolPicked(EditTool.RETAKE, "doc1", "page1", ImageFilter.ORIGINAL, null)
        val event = viewModel.event.value
        assertTrue(event is ReaderEditViewModel.EditEvent.OpenRetake)
        val retake = event as ReaderEditViewModel.EditEvent.OpenRetake
        assertEquals("doc1", retake.documentId)
        assertEquals("page1", retake.pageId)
    }

    @Test
    fun onToolPicked_nullCurrentPage_swallowsTargetedTools() {
        viewModel.onToolPicked(EditTool.CROP, "doc1", null, ImageFilter.ORIGINAL, null)
        // Crop needs a page; should not emit a navigation event.
        assertNull(viewModel.event.value)
    }

    @Test
    fun setPageTitle_persistsAndPushesUndo() = runTest {
        coEvery { repository.setPageTitle("p1", "Cover") } returns Unit
        viewModel.setPageTitle("p1", previous = null, next = "Cover")
        advanceUntilIdle()
        coVerify { repository.setPageTitle("p1", "Cover") }
        assertTrue(viewModel.undoAvailable.value)
    }

    @Test
    fun setPageTitle_blankNewValue_clearsTitle() = runTest {
        coEvery { repository.setPageTitle("p1", null) } returns Unit
        viewModel.setPageTitle("p1", previous = "Cover", next = "   ")
        advanceUntilIdle()
        coVerify { repository.setPageTitle("p1", null) }
    }

    @Test
    fun setPageTitle_noChange_skipsWrite() = runTest {
        viewModel.setPageTitle("p1", previous = "Cover", next = "Cover")
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.setPageTitle(any(), any()) }
        assertFalse(viewModel.undoAvailable.value)
    }

    @Test
    fun undo_revertsTitleChange() = runTest {
        coEvery { repository.setPageTitle(any(), any()) } returns Unit
        viewModel.setPageTitle("p1", previous = null, next = "Cover")
        advanceUntilIdle()
        viewModel.undo()
        advanceUntilIdle()
        coVerify { repository.setPageTitle("p1", null) }
        assertFalse(viewModel.undoAvailable.value)
    }

    @Test
    fun consumeEvent_clearsEventChannel() {
        viewModel.onToolPicked(EditTool.BRUSH, "doc1", "page1", ImageFilter.ORIGINAL, null)
        assertTrue(viewModel.event.value is ReaderEditViewModel.EditEvent.ComingSoon)
        viewModel.consumeEvent()
        assertNull(viewModel.event.value)
    }
}
