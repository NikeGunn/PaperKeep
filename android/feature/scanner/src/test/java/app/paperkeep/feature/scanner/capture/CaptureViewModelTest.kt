package app.paperkeep.feature.scanner.capture

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.imaging.DetectionResult
import app.paperkeep.core.imaging.EdgeDetector
import app.paperkeep.core.imaging.FakeEdgeDetector
import app.paperkeep.core.imaging.Point2f
import app.paperkeep.core.imaging.Quad
import io.mockk.every
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for 1B.13 (capture pipeline) and 1B.14 (crop screen state + rotation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CaptureViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testDispatchers = object : AppDispatchers {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
    }

    private lateinit var viewModel: CaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 1B.13: Perspective transform with detected quad ───────────────────────

    @Test
    fun onImageCaptured_withDocumentBitmap_transitionsToReadyToCrop() = runTest {
        viewModel = buildViewModel(FakeEdgeDetector())
        val bitmap = createNonUniformBitmap(640, 480)

        viewModel.onImageCaptured(bitmap)
        advanceUntilIdle()

        assertTrue(
            "Must transition to ReadyToCrop",
            viewModel.state.value is CaptureState.ReadyToCrop
        )
    }

    @Test
    fun onImageCaptured_readyToCrop_hasNonNullImage() = runTest {
        viewModel = buildViewModel(FakeEdgeDetector())
        val bitmap = createNonUniformBitmap(640, 480)

        viewModel.onImageCaptured(bitmap)
        advanceUntilIdle()

        val state = viewModel.state.value as CaptureState.ReadyToCrop
        assertNotNull(state.image)
    }

    // ── 1B.13: Capture handles large image (4000×3000) ───────────────────────

    @Test
    fun onImageCaptured_handlesLargeImage() = runTest {
        viewModel = buildViewModel(FakeEdgeDetector())
        val bitmap = createNonUniformBitmap(4000, 3000)

        viewModel.onImageCaptured(bitmap)
        advanceUntilIdle()

        // Must not crash and must produce a valid state
        val state = viewModel.state.value
        assertTrue(state is CaptureState.ReadyToCrop || state is CaptureState.Error)
    }

    // ── 1B.13: Corrupted image data — no crash ────────────────────────────────

    @Test
    fun onImageCaptured_whenDetectorThrows_transitionsToError() = runTest {
        val badDetector = mockk<EdgeDetector>()
        every { badDetector.detect(any()) } throws RuntimeException("Native crash")
        viewModel = buildViewModel(badDetector)
        val bitmap = createUniformBitmap(100, 100)

        viewModel.onImageCaptured(bitmap)
        advanceUntilIdle()

        assertTrue(
            "Exception in pipeline must result in Error state",
            viewModel.state.value is CaptureState.Error
        )
    }

    // ── 1B.14: Crop screen — rotate button ────────────────────────────────────

    @Test
    fun rotateImage_updatesImageInReadyToCropState() = runTest {
        viewModel = buildViewModel(FakeEdgeDetector())
        val bitmap = createNonUniformBitmap(200, 100) // landscape

        viewModel.onImageCaptured(bitmap)
        advanceUntilIdle()

        val beforeRotate = (viewModel.state.value as CaptureState.ReadyToCrop).image

        viewModel.rotateImage()
        advanceUntilIdle()

        val afterRotate = (viewModel.state.value as CaptureState.ReadyToCrop).image
        // After 90° rotation, width and height should be swapped
        assertEquals(beforeRotate.height, afterRotate.width)
        assertEquals(beforeRotate.width, afterRotate.height)
    }

    // ── 1B.14: Retake navigates back to Idle ──────────────────────────────────

    @Test
    fun retake_resetsStateToIdle() = runTest {
        viewModel = buildViewModel(FakeEdgeDetector())
        viewModel.onImageCaptured(createNonUniformBitmap(200, 150))
        advanceUntilIdle()

        viewModel.retake()

        assertEquals(CaptureState.Idle, viewModel.state.value)
    }

    // ── 1B.14: State survives rotation (rememberSaveable / SavedStateHandle) ──

    @Test
    fun savedState_persistsAndRestoresQuad() = runTest {
        val savedStateHandle = SavedStateHandle()
        viewModel = buildViewModel(FakeEdgeDetector(), savedStateHandle)

        val quad = Quad(
            topLeft = Point2f(10f, 20f),
            topRight = Point2f(300f, 20f),
            bottomRight = Point2f(300f, 400f),
            bottomLeft = Point2f(10f, 400f),
        )

        viewModel.onImageCaptured(createNonUniformBitmap(320, 480))
        advanceUntilIdle()

        // Simulate updating quad (as if user dragged corners)
        viewModel.onQuadUpdated(quad)
        advanceUntilIdle()

        // Restore from saved state (simulates activity recreation)
        val restored = viewModel.restoreQuadFromSavedState()

        assertNotNull(restored)
        assertEquals(quad.topLeft.x, restored!!.topLeft.x, 0.01f)
        assertEquals(quad.topLeft.y, restored.topLeft.y, 0.01f)
    }

    // ── 1B.14: Quad update triggers re-warp ───────────────────────────────────

    @Test
    fun onQuadUpdated_updatesReadyToCropState() = runTest {
        viewModel = buildViewModel(FakeEdgeDetector())
        viewModel.onImageCaptured(createNonUniformBitmap(320, 480))
        advanceUntilIdle()

        val newQuad = Quad(
            topLeft = Point2f(20f, 20f),
            topRight = Point2f(300f, 20f),
            bottomRight = Point2f(300f, 460f),
            bottomLeft = Point2f(20f, 460f),
        )

        viewModel.onQuadUpdated(newQuad)
        advanceUntilIdle()

        val state = viewModel.state.value as CaptureState.ReadyToCrop
        assertEquals(newQuad, state.quad)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun buildViewModel(
        detector: EdgeDetector,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = CaptureViewModel(detector, testDispatchers, savedStateHandle)

    private fun createUniformBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE) }

    private fun createNonUniformBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.eraseColor(Color.WHITE)
            val mX = (w * 0.15).toInt()
            val mY = (h * 0.15).toInt()
            for (y in mY until (h - mY)) for (x in mX until (w - mX)) bmp.setPixel(x, y, Color.BLACK)
        }
}
