package app.paperkeep.feature.scanner.recent

import app.paperkeep.core.data.db.ScanDao
import app.paperkeep.core.data.db.ScanEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for 1B.17: Recent scans thumbnail strip ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentScansViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 1B.17: Thumbnail strip renders with scans ─────────────────────────────

    @Test
    fun recentScans_withScansInDao_emitsScans() = runTest {
        val scans = listOf(
            buildEntity("a", "Doc A"),
            buildEntity("b", "Doc B"),
        )
        val dao = mockk<ScanDao> {
            every { observeRecent(10) } returns flowOf(scans)
        }
        val viewModel = RecentScansViewModel(dao)

        val results = mutableListOf<List<ScanEntity>>()
        val job = launch { viewModel.recentScans.toList(results) }
        advanceUntilIdle()
        job.cancel()

        assertTrue("Must have emitted at least one value", results.isNotEmpty())
        // The initial value is emptyList(), then the flow emits scans
        val lastEmitted = results.last()
        assertEquals(2, lastEmitted.size)
    }

    // ── 1B.17: Empty state when no scans ─────────────────────────────────────

    @Test
    fun recentScans_whenEmpty_emitsEmptyList() = runTest {
        val dao = mockk<ScanDao> {
            every { observeRecent(10) } returns flowOf(emptyList())
        }
        val viewModel = RecentScansViewModel(dao)

        val results = mutableListOf<List<ScanEntity>>()
        val job = launch { viewModel.recentScans.toList(results) }
        advanceUntilIdle()
        job.cancel()

        assertTrue(results.isNotEmpty())
        assertTrue(results.last().isEmpty())
    }

    // ── 1B.17: Initial value is empty list before dao emits ──────────────────

    @Test
    fun recentScans_initialValue_isEmptyList() = runTest {
        val dao = mockk<ScanDao> {
            every { observeRecent(10) } returns flowOf(emptyList())
        }
        val viewModel = RecentScansViewModel(dao)

        // Before any collection/advancement, stateIn initialValue is emptyList
        assertTrue(viewModel.recentScans.value.isEmpty())
    }

    // ── 1B.17: Thumbnail strip constants ─────────────────────────────────────

    @Test
    fun thumbnailStripTags_areNonEmpty() {
        assertTrue(TAG_THUMBNAIL_STRIP.isNotBlank())
        assertTrue(TAG_THUMBNAIL_EMPTY.isNotBlank())
        assertTrue(TAG_THUMBNAIL_ITEM.isNotBlank())
    }

    @Test
    fun thumbnailStripTags_areUnique() {
        val tags = setOf(TAG_THUMBNAIL_STRIP, TAG_THUMBNAIL_EMPTY, TAG_THUMBNAIL_ITEM)
        assertEquals(3, tags.size)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildEntity(id: String, title: String) = ScanEntity(
        id = id,
        title = title,
        pageCount = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        originalPath = "/files/$id.enc",
        thumbnailPath = "/files/$id.thumb.enc",
        widthPx = 1920,
        heightPx = 1440,
    )
}
