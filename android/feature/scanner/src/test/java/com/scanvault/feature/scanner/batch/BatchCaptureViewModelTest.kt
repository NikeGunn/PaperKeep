package com.scanvault.feature.scanner.batch

import android.graphics.Bitmap
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [BatchCaptureViewModel] — multi-page batch flow (2B.5).
 *
 * Tests:
 *  - Batch starts empty
 *  - addPage appends pages
 *  - movePage reorders correctly
 *  - deletePage removes by ID
 *  - deletePageAt removes by index
 *  - clearBatch resets to empty
 *  - consumePages returns pages and clears
 *  - hasPages and pageCount computed properties
 *  - movePage with out-of-bounds indices is no-op (safe)
 *  - State survives ViewModel re-subscription (cold start simulation)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BatchCaptureViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var vm: BatchCaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vm = BatchCaptureViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 2B.5: Batch starts empty ──────────────────────────────────────────────

    @Test
    fun initialState_isEmpty() = runTest {
        vm.pages.test {
            val pages = awaitItem()
            assertTrue(pages.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hasPages_isFalse_whenEmpty() {
        assertFalse(vm.hasPages)
    }

    @Test
    fun pageCount_isZero_whenEmpty() {
        assertEquals(0, vm.pageCount)
    }

    // ── 2B.5: addPage appends pages in order ─────────────────────────────────

    @Test
    fun addPage_appendsToList() = runTest {
        val bmp1 = fakeBitmap()
        val bmp2 = fakeBitmap()

        vm.pages.test {
            awaitItem() // initial empty

            vm.addPage(bmp1)
            val after1 = awaitItem()
            assertEquals(1, after1.size)
            assertEquals(bmp1, after1[0].bitmap)

            vm.addPage(bmp2)
            val after2 = awaitItem()
            assertEquals(2, after2.size)
            assertEquals(bmp2, after2[1].bitmap)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun addPage_setsUniqueIds() = runTest {
        repeat(3) { vm.addPage(fakeBitmap()) }
        testDispatcher.scheduler.advanceUntilIdle()

        val ids = vm.pages.value.map { it.id }
        assertEquals(3, ids.toSet().size) // all unique
    }

    @Test
    fun hasPages_isTrueAfterAdd() = runTest {
        vm.addPage(fakeBitmap())
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.hasPages)
    }

    @Test
    fun pageCount_incrementsAfterAdd() = runTest {
        vm.addPage(fakeBitmap())
        vm.addPage(fakeBitmap())
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.pageCount)
    }

    // ── 2B.5: movePage reorders correctly ─────────────────────────────────────

    @Test
    fun movePage_movesFromFirstToLast() = runTest {
        val bitmaps = List(3) { fakeBitmap() }
        bitmaps.forEach { vm.addPage(it) }
        testDispatcher.scheduler.advanceUntilIdle()

        val idFirst = vm.pages.value[0].id

        vm.movePage(fromIndex = 0, toIndex = 2)
        testDispatcher.scheduler.advanceUntilIdle()

        // The page that was first is now last
        assertEquals(idFirst, vm.pages.value[2].id)
    }

    @Test
    fun movePage_movesFromLastToFirst() = runTest {
        val bitmaps = List(3) { fakeBitmap() }
        bitmaps.forEach { vm.addPage(it) }
        testDispatcher.scheduler.advanceUntilIdle()

        val idLast = vm.pages.value[2].id

        vm.movePage(fromIndex = 2, toIndex = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(idLast, vm.pages.value[0].id)
    }

    @Test
    fun movePage_sameIndex_isNoOp() = runTest {
        val bitmaps = List(2) { fakeBitmap() }
        bitmaps.forEach { vm.addPage(it) }
        testDispatcher.scheduler.advanceUntilIdle()

        val idsBefore = vm.pages.value.map { it.id }
        vm.movePage(1, 1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(idsBefore, vm.pages.value.map { it.id })
    }

    @Test
    fun movePage_outOfBoundsIndex_isNoOp() = runTest {
        vm.addPage(fakeBitmap())
        testDispatcher.scheduler.advanceUntilIdle()

        val idsBefore = vm.pages.value.map { it.id }
        vm.movePage(0, 99) // index 99 out of range
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(idsBefore, vm.pages.value.map { it.id })
    }

    // ── 2B.5: deletePage removes by ID ────────────────────────────────────────

    @Test
    fun deletePage_removesCorrectPage() = runTest {
        val bitmaps = List(3) { fakeBitmap() }
        bitmaps.forEach { vm.addPage(it) }
        testDispatcher.scheduler.advanceUntilIdle()

        val idToDelete = vm.pages.value[1].id
        vm.deletePage(idToDelete)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.pageCount)
        assertFalse(vm.pages.value.any { it.id == idToDelete })
    }

    @Test
    fun deletePage_unknownId_isNoOp() = runTest {
        vm.addPage(fakeBitmap())
        testDispatcher.scheduler.advanceUntilIdle()

        vm.deletePage("nonexistent-id")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.pageCount)
    }

    // ── 2B.5: deletePageAt removes by index ───────────────────────────────────

    @Test
    fun deletePageAt_removesByIndex() = runTest {
        val bitmaps = List(3) { fakeBitmap() }
        bitmaps.forEach { vm.addPage(it) }
        testDispatcher.scheduler.advanceUntilIdle()

        val idAtIndex1 = vm.pages.value[1].id
        vm.deletePageAt(1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.pageCount)
        assertFalse(vm.pages.value.any { it.id == idAtIndex1 })
    }

    @Test
    fun deletePageAt_outOfBoundsIndex_isNoOp() = runTest {
        vm.addPage(fakeBitmap())
        testDispatcher.scheduler.advanceUntilIdle()

        vm.deletePageAt(99)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.pageCount)
    }

    // ── 2B.5: clearBatch resets to empty ──────────────────────────────────────

    @Test
    fun clearBatch_removesAllPages() = runTest {
        repeat(3) { vm.addPage(fakeBitmap()) }
        testDispatcher.scheduler.advanceUntilIdle()

        vm.clearBatch()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.pages.value.isEmpty())
        assertFalse(vm.hasPages)
        assertEquals(0, vm.pageCount)
    }

    // ── 2B.5: consumePages returns and clears ─────────────────────────────────

    @Test
    fun consumePages_returnsAllPages_thenClears() = runTest {
        val bitmaps = List(3) { fakeBitmap() }
        bitmaps.forEach { vm.addPage(it) }
        testDispatcher.scheduler.advanceUntilIdle()

        val consumed = vm.consumePages()

        assertEquals(3, consumed.size)
        assertTrue(vm.pages.value.isEmpty())
    }

    @Test
    fun consumePages_onEmpty_returnsEmpty() = runTest {
        val consumed = vm.consumePages()
        assertTrue(consumed.isEmpty())
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun fakeBitmap(): Bitmap =
        Bitmap.createBitmap(100, 141, Bitmap.Config.ARGB_8888)
}
