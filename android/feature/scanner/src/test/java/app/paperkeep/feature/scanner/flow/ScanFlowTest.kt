package app.paperkeep.feature.scanner.flow

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.imaging.FakeEdgeDetector
import app.paperkeep.core.imaging.Point2f
import app.paperkeep.core.imaging.Quad
import app.paperkeep.feature.scanner.camera.CameraControlsViewModel
import app.paperkeep.feature.scanner.permission.CameraPermissionState
import app.paperkeep.feature.scanner.capture.CaptureState
import app.paperkeep.feature.scanner.capture.CaptureViewModel
import app.paperkeep.feature.scanner.edge.EdgeDetectionViewModel
import app.paperkeep.feature.scanner.edge.EdgeOverlayState
import app.paperkeep.feature.scanner.permission.CameraPermissionViewModel
import app.paperkeep.feature.scanner.camera.CameraControlsState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 1B.20: Comprehensive test of the full scan flow across all ViewModels.
 *
 * Simulates the complete user journey:
 *  1. Grant camera permission
 *  2. Start camera with controls (torch, grid, zoom)
 *  3. Live edge detection on preview frames
 *  4. Capture → perspective transform
 *  5. Adjust crop quad on crop screen
 *  6. Rotate image
 *  7. Retake → back to idle
 *  8. State survives simulated rotation (SavedStateHandle)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScanFlowTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : AppDispatchers {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Step 1: Permission granted ─────────────────────────────────────────────

    @Test
    fun step1_cameraPermission_grantedFlow() = runTest {
        val permVm = CameraPermissionViewModel()

        permVm.onPermissionResult(granted = true, canShowRationale = false)

        assertEquals(CameraPermissionState.Granted, permVm.state.value)
    }

    // ── Step 2: Camera controls initialise correctly ──────────────────────────

    @Test
    fun step2_cameraControls_initialState() = runTest {
        val controlsVm = CameraControlsViewModel()

        val state = controlsVm.state.value

        assertFalse("Torch off initially", state.isTorchOn)
        assertFalse("Grid hidden initially", state.isGridVisible)
        assertEquals(1.0f, state.zoomRatio, 0.001f)
    }

    // ── Step 3: Live edge detection on preview ────────────────────────────────

    @Test
    fun step3_edgeDetection_documentBitmap_transitionsToGood() = runTest {
        val edgeVm = EdgeDetectionViewModel(FakeEdgeDetector(), testDispatchers)

        edgeVm.processFrame(makeDocumentBitmap())
        advanceUntilIdle()

        assertTrue(edgeVm.overlayState.value is EdgeOverlayState.Good)
    }

    @Test
    fun step3_edgeDetection_uniformBitmap_stays_none() = runTest {
        val edgeVm = EdgeDetectionViewModel(FakeEdgeDetector(), testDispatchers)

        edgeVm.processFrame(makeUniformBitmap())
        advanceUntilIdle()

        assertEquals(EdgeOverlayState.None, edgeVm.overlayState.value)
    }

    // ── Step 4: Capture → perspective warp ────────────────────────────────────

    @Test
    fun step4_capture_transitionsToReadyToCrop() = runTest {
        val captureVm = buildCaptureVm()

        captureVm.onImageCaptured(makeDocumentBitmap())
        advanceUntilIdle()

        assertTrue(captureVm.state.value is CaptureState.ReadyToCrop)
    }

    @Test
    fun step4_capture_readyState_imageIsNotNull() = runTest {
        val captureVm = buildCaptureVm()

        captureVm.onImageCaptured(makeDocumentBitmap())
        advanceUntilIdle()

        val state = captureVm.state.value as CaptureState.ReadyToCrop
        assertNotNull(state.image)
    }

    // ── Step 5: Adjust crop quad ──────────────────────────────────────────────

    @Test
    fun step5_cropQuadUpdate_persistsInState() = runTest {
        val captureVm = buildCaptureVm()
        captureVm.onImageCaptured(makeDocumentBitmap())
        advanceUntilIdle()

        val newQuad = Quad(
            topLeft = Point2f(20f, 20f),
            topRight = Point2f(280f, 20f),
            bottomRight = Point2f(280f, 460f),
            bottomLeft = Point2f(20f, 460f),
        )
        captureVm.onQuadUpdated(newQuad)
        advanceUntilIdle()

        val state = captureVm.state.value as CaptureState.ReadyToCrop
        assertEquals(newQuad, state.quad)
    }

    // ── Step 6: Rotate image ──────────────────────────────────────────────────

    @Test
    fun step6_rotate_swapsDimensions() = runTest {
        val captureVm = buildCaptureVm()
        captureVm.onImageCaptured(makeDocumentBitmap(width = 300, height = 400))
        advanceUntilIdle()

        val before = (captureVm.state.value as CaptureState.ReadyToCrop).image

        captureVm.rotateImage()
        advanceUntilIdle()

        val after = (captureVm.state.value as CaptureState.ReadyToCrop).image
        assertEquals(before.height, after.width)
        assertEquals(before.width, after.height)
    }

    // ── Step 7: Retake → Idle ─────────────────────────────────────────────────

    @Test
    fun step7_retake_resetsToIdle() = runTest {
        val captureVm = buildCaptureVm()
        captureVm.onImageCaptured(makeDocumentBitmap())
        advanceUntilIdle()
        assertTrue(captureVm.state.value is CaptureState.ReadyToCrop)

        captureVm.retake()

        assertEquals(CaptureState.Idle, captureVm.state.value)
    }

    // ── Step 8: Full flow + rotation ─────────────────────────────────────────

    @Test
    fun step8_fullFlow_withRotation_quadPersists() = runTest {
        val savedState = SavedStateHandle()
        val captureVm = buildCaptureVm(savedState)

        captureVm.onImageCaptured(makeDocumentBitmap())
        advanceUntilIdle()

        val quad = Quad(
            topLeft = Point2f(5f, 5f),
            topRight = Point2f(295f, 5f),
            bottomRight = Point2f(295f, 395f),
            bottomLeft = Point2f(5f, 395f),
        )
        captureVm.onQuadUpdated(quad)
        advanceUntilIdle()

        // Simulate rotation
        val recreated = buildCaptureVm(savedState)
        val restored = recreated.restoreQuadFromSavedState()

        assertNotNull(restored)
        assertEquals(quad.topLeft.x, restored!!.topLeft.x, 0.01f)
    }

    // ── Camera controls during scan ───────────────────────────────────────────

    @Test
    fun controls_torch_andGrid_independentlyToggleable() = runTest {
        val vm = CameraControlsViewModel()

        vm.toggleTorch()
        vm.toggleGrid()

        assertTrue(vm.state.value.isTorchOn)
        assertTrue(vm.state.value.isGridVisible)

        vm.toggleGrid()

        assertTrue(vm.state.value.isTorchOn)
        assertFalse(vm.state.value.isGridVisible)
    }

    @Test
    fun controls_zoom_clampedToDeviceBounds() = runTest {
        val vm = CameraControlsViewModel()
        vm.setZoomBounds(min = 0.6f, max = 3.0f)

        vm.onZoomChanged(10.0f)
        assertEquals(3.0f, vm.state.value.zoomRatio, 0.001f)

        vm.onZoomChanged(0.1f)
        assertEquals(0.6f, vm.state.value.zoomRatio, 0.001f)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildCaptureVm(savedState: SavedStateHandle = SavedStateHandle()) =
        CaptureViewModel(FakeEdgeDetector(), app.paperkeep.core.ml.DocumentClassifier(), testDispatchers, savedState)

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
