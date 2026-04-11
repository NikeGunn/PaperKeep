package com.scanvault.feature.library

import app.cash.turbine.test
import com.scanvault.core.data.repository.DocumentRepository
import com.scanvault.core.domain.model.Document
import com.scanvault.core.domain.model.DocumentSort
import com.scanvault.core.domain.model.Folder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.time.Instant

/**
 * Unit tests for [LibraryViewModel].
 * Repository is mocked — no Room or disk I/O.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: DocumentRepository
    private lateinit var vm: LibraryViewModel

    private val docsFlow = MutableStateFlow<List<Document>>(emptyList())
    private val foldersFlow = MutableStateFlow<List<Folder>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true) {
            every { observeDocuments(any()) } returns docsFlow
            every { observeFolders() } returns foldersFlow
        }
        vm = LibraryViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun initialState_isEmpty_andSortIsNewest() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.documents.isEmpty())
            assertEquals(DocumentSort.NEWEST, state.sort)
            assertFalse(state.isMultiSelect)
        }
    }

    // ── Sort ─────────────────────────────────────────────────────────────────

    @Test
    fun setSort_updatesStateSort() = runTest {
        vm.uiState.test {
            awaitItem() // initial

            vm.setSort(DocumentSort.TITLE_AZ)
            testDispatcher.scheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertEquals(DocumentSort.TITLE_AZ, updated.sort)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Multi-select ──────────────────────────────────────────────────────────

    @Test
    fun toggleSelection_addsIdToSelectedSet() = runTest {
        vm.uiState.test {
            awaitItem() // initial

            vm.toggleSelection("doc-1")
            testDispatcher.scheduler.advanceUntilIdle()

            val state = awaitItem()
            assertTrue(state.selectedIds.contains("doc-1"))
            assertTrue(state.isMultiSelect)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleSelection_twice_removesId() = runTest {
        vm.uiState.test {
            awaitItem() // initial

            vm.toggleSelection("doc-1")
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // added

            vm.toggleSelection("doc-1")
            testDispatcher.scheduler.advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.selectedIds.contains("doc-1"))
            assertFalse(state.isMultiSelect)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearSelection_emptiesSelectedSet() = runTest {
        vm.toggleSelection("doc-1")
        vm.toggleSelection("doc-2")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.clearSelection()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.selectedIds.isEmpty())
        }
    }

    // ── Delete selected ───────────────────────────────────────────────────────

    @Test
    fun deleteSelected_callsRepoForEachId_thenClearsSelection() = runTest {
        coEvery { repo.deleteDocumentById(any()) } returns Unit

        vm.toggleSelection("doc-a")
        vm.toggleSelection("doc-b")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.deleteSelected()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repo.deleteDocumentById("doc-a") }
        coVerify { repo.deleteDocumentById("doc-b") }

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.selectedIds.isEmpty())
        }
    }

    // ── Folder operations ─────────────────────────────────────────────────────

    @Test
    fun createFolder_callsRepoCreate() = runTest {
        coEvery { repo.createFolder(any()) } returns Unit

        vm.createFolder("Work")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repo.createFolder(match { it.name == "Work" }) }
    }

    @Test
    fun renameFolder_callsRepoUpdate_withNewName() = runTest {
        coEvery { repo.updateFolder(any()) } returns Unit
        val folder = buildFolder("f1", "Old")

        vm.renameFolder(folder, "New")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repo.updateFolder(match { it.name == "New" }) }
    }

    @Test
    fun deleteFolder_callsRepoDelete() = runTest {
        coEvery { repo.deleteFolder(any()) } returns Unit
        val folder = buildFolder("f1", "Work")

        vm.deleteFolder(folder)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repo.deleteFolder(folder) }
    }

    @Test
    fun deleteFolder_clearsActiveFolderIfMatches() = runTest {
        coEvery { repo.deleteFolder(any()) } returns Unit
        val folder = buildFolder("f1", "Work")

        vm.setActiveFolder("f1")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.deleteFolder(folder)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertNull(state.activeFolderId)
        }
    }

    // ── Move to folder ────────────────────────────────────────────────────────

    @Test
    fun moveSelectedToFolder_callsRepo_andClearsSelection() = runTest {
        coEvery { repo.moveDocumentToFolder(any(), any()) } returns Unit

        vm.toggleSelection("d1")
        vm.toggleSelection("d2")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveSelectedToFolder("folder-1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repo.moveDocumentToFolder("d1", "folder-1") }
        coVerify { repo.moveDocumentToFolder("d2", "folder-1") }

        vm.uiState.test {
            assertTrue(awaitItem().selectedIds.isEmpty())
        }
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Test
    fun refresh_setsIsRefreshingThenClears() = runTest {
        vm.uiState.test {
            awaitItem() // initial

            vm.refresh()
            testDispatcher.scheduler.advanceUntilIdle()

            // After the 300ms delay completes, isRefreshing should be false again
            val state = expectMostRecentItem()
            assertFalse(state.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Documents flow reflected in UI state ──────────────────────────────────

    @Test
    fun documentsFlow_updatesUiState() = runTest {
        vm.uiState.test {
            awaitItem() // initial empty

            val docs = listOf(buildDocument("d1"))
            docsFlow.value = docs
            testDispatcher.scheduler.advanceUntilIdle()

            val state = awaitItem()
            assertEquals(1, state.documents.size)
            assertEquals("d1", state.documents[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildDocument(id: String) = Document(
        id = id,
        title = "Document $id",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        folderId = null,
        pageCount = 1,
        colorTag = null,
        pages = emptyList(),
    )

    private fun buildFolder(id: String, name: String) = Folder(
        id = id,
        name = name,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
