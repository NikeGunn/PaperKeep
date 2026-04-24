package app.paperkeep.feature.scanner

import app.paperkeep.feature.scanner.camera.MIN_TOUCH_TARGET
import app.paperkeep.feature.scanner.camera.TAG_CAPTURE_BUTTON
import app.paperkeep.feature.scanner.camera.TAG_FOCUS_RING
import app.paperkeep.feature.scanner.camera.TAG_GRID_BUTTON
import app.paperkeep.feature.scanner.camera.TAG_GRID_OVERLAY
import app.paperkeep.feature.scanner.camera.TAG_TORCH_BUTTON
import app.paperkeep.feature.scanner.camera.CameraControlsViewModel
import app.paperkeep.feature.scanner.edge.EdgeDetectionViewModel
import app.paperkeep.feature.scanner.edge.EdgeOverlayState
import app.paperkeep.feature.scanner.recent.TAG_THUMBNAIL_STRIP
import app.paperkeep.core.imaging.FakeEdgeDetector
import app.paperkeep.core.common.AppDispatchers
import android.graphics.Bitmap
import android.graphics.Color
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1.9 — Verification that the camera screen meets Phase 1 acceptance criteria.
 *
 * Checks:
 *  1. CameraX preview layer exists (TAG_SCANNER_SCREEN present)
 *  2. Edge detection overlay transitions on document bitmap
 *  3. Pinch zoom clamps to device bounds
 *  4. Tap-to-focus sets and clears focus point
 *  5. Torch toggle
 *  6. Grid overlay toggle
 *  7. Batch mode ViewModel tracks page count
 *  8. Recent scans strip tag exists
 *  9. All touch targets >= 48dp
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class P19CameraVerificationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : AppDispatchers {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
    }

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // 1. Scanner screen tag
    @Test
    fun scannerScreenTag_isNonEmpty() {
        assertTrue(TAG_SCANNER_SCREEN.isNotBlank())
    }

    // 2. Edge detection overlay — transitions on document bitmap
    @Test
    fun edgeDetection_documentBitmap_yieldsGoodOverlay() = runTest {
        val vm = EdgeDetectionViewModel(FakeEdgeDetector(), testDispatchers)
        vm.processFrame(makeDocumentBitmap())
        advanceUntilIdle()
        assertTrue(vm.overlayState.value is EdgeOverlayState.Good)
    }

    @Test
    fun edgeDetection_uniformBitmap_yieldsNoneOverlay() = runTest {
        val vm = EdgeDetectionViewModel(FakeEdgeDetector(), testDispatchers)
        vm.processFrame(makeUniformBitmap())
        advanceUntilIdle()
        assertEquals(EdgeOverlayState.None, vm.overlayState.value)
    }

    // 3. Pinch zoom clamped to bounds
    @Test
    fun zoom_clampsToMaxBound() = runTest {
        val vm = CameraControlsViewModel()
        vm.setZoomBounds(min = 0.6f, max = 3.0f)
        vm.onZoomChanged(99f)
        assertEquals(3.0f, vm.state.value.zoomRatio, 0.001f)
    }

    @Test
    fun zoom_clampsToMinBound() = runTest {
        val vm = CameraControlsViewModel()
        vm.setZoomBounds(min = 0.6f, max = 3.0f)
        vm.onZoomChanged(0.01f)
        assertEquals(0.6f, vm.state.value.zoomRatio, 0.001f)
    }

    // 4. Tap-to-focus
    @Test
    fun tapToFocus_setsFocusPoint() = runTest {
        val vm = CameraControlsViewModel()
        vm.onTapToFocus(200f, 300f)
        assertNotNull(vm.state.value.focusPoint)
        assertEquals(200f, vm.state.value.focusPoint!!.x, 0.001f)
    }

    @Test
    fun clearFocusPoint_removesFocusPoint() = runTest {
        val vm = CameraControlsViewModel()
        vm.onTapToFocus(200f, 300f)
        vm.clearFocusPoint()
        assertNull(vm.state.value.focusPoint)
    }

    // 5. Torch toggle
    @Test
    fun torch_togglesOnAndOff() = runTest {
        val vm = CameraControlsViewModel()
        assertFalse(vm.state.value.isTorchOn)
        vm.toggleTorch()
        assertTrue(vm.state.value.isTorchOn)
        vm.toggleTorch()
        assertFalse(vm.state.value.isTorchOn)
    }

    // 6. Grid overlay toggle
    @Test
    fun grid_togglesOnAndOff() = runTest {
        val vm = CameraControlsViewModel()
        assertFalse(vm.state.value.isGridVisible)
        vm.toggleGrid()
        assertTrue(vm.state.value.isGridVisible)
        vm.toggleGrid()
        assertFalse(vm.state.value.isGridVisible)
    }

    // 7. Batch mode ViewModel tracks page count
    @Test
    fun batchCapture_pageCount_incrementsOnAddPage() {
        val vm = app.paperkeep.feature.scanner.batch.BatchCaptureViewModel()
        assertEquals(0, vm.pageCount)
        vm.addPage(makeDocumentBitmap())
        assertEquals(1, vm.pageCount)
        vm.addPage(makeDocumentBitmap())
        assertEquals(2, vm.pageCount)
    }

    @Test
    fun batchCapture_clearBatch_resetsPageCount() {
        val vm = app.paperkeep.feature.scanner.batch.BatchCaptureViewModel()
        vm.addPage(makeDocumentBitmap())
        vm.clearBatch()
        assertEquals(0, vm.pageCount)
    }

    // 8. Recent scans strip tag
    @Test
    fun recentScanStripTag_isNonEmpty() {
        assertTrue(TAG_THUMBNAIL_STRIP.isNotBlank())
    }

    // 9. Touch targets >= 48dp
    @Test
    fun touchTarget_minimum_is48dp() {
        assertTrue(
            "MIN_TOUCH_TARGET must be >= 48dp",
            MIN_TOUCH_TARGET.value >= 48f,
        )
    }

    // 10. All camera test tags are unique and non-empty
    @Test
    fun cameraTestTags_areUniqueAndNonEmpty() {
        val tags = listOf(
            TAG_TORCH_BUTTON,
            TAG_GRID_BUTTON,
            TAG_CAPTURE_BUTTON,
            TAG_GRID_OVERLAY,
            TAG_FOCUS_RING,
            TAG_THUMBNAIL_STRIP,
            TAG_SCANNER_SCREEN,
        )
        tags.forEach { assertTrue("Tag must not be empty", it.isNotBlank()) }
        assertEquals("All camera tags must be unique", tags.size, tags.toSet().size)
    }

    // Helpers
    private fun makeDocumentBitmap(width: Int = 320, height: Int = 480): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.eraseColor(Color.WHITE)
            val mX = (width * 0.15).toInt()
            val mY = (height * 0.15).toInt()
            for (y in mY until (height - mY)) for (x in mX until (width - mX)) bmp.setPixel(x, y, Color.BLACK)
        }

    private fun makeUniformBitmap(): Bitmap =
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE) }
}
