package app.paperkeep.feature.scanner.edge

import android.graphics.Bitmap
import android.graphics.Color
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.imaging.DetectionResult
import app.paperkeep.core.imaging.EdgeDetector
import app.paperkeep.core.imaging.FakeEdgeDetector
import app.paperkeep.core.imaging.Point2f
import app.paperkeep.core.imaging.Quad
import app.paperkeep.core.imaging.StabilityTracker
import io.mockk.every
import io.mockk.mockk
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for EdgeDetectionViewModel — covers the stability tracker
 * integration and the Magic Scan auto-capture signal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EdgeDetectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: EdgeDetectionViewModel

    private val testDispatchers = object : AppDispatchers {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
    }

    private var fakeNow: Long = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeNow = 0L
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun newViewModel(detector: EdgeDetector): EdgeDetectionViewModel {
        return EdgeDetectionViewModel(detector, testDispatchers).also {
            it.clockMs = { fakeNow }
        }
    }

    // ── A detected quad on the first frame puts the overlay in Settling. ─────

    @Test
    fun processFrame_withDocumentBitmap_transitionsToSettling() = runTest {
        viewModel = newViewModel(FakeEdgeDetector())
        viewModel.processFrame(createNonUniformBitmap())
        advanceUntilIdle()

        assertTrue(
            "First detection should produce a Settling state",
            viewModel.overlayState.value is EdgeOverlayState.Settling,
        )
    }

    // ── Holding the quad still for > 1.5s locks the overlay and emits auto-capture.

    @Test
    fun heldQuad_transitionsToLocked_andEmitsAutoCapture() = runTest {
        val quad = Quad(
            topLeft = Point2f(10f, 10f),
            topRight = Point2f(90f, 10f),
            bottomRight = Point2f(90f, 90f),
            bottomLeft = Point2f(10f, 90f),
        )
        val detector = mockk<EdgeDetector>()
        every { detector.detect(any()) } returns DetectionResult.Found(quad)
        viewModel = newViewModel(detector)

        viewModel.autoCaptureSignals.test(timeout = 5.seconds) {
            // Drive the pipeline only AFTER Turbine has subscribed.
            viewModel.processFrame(createNonUniformBitmap())
            advanceUntilIdle()
            assertTrue(viewModel.overlayState.value is EdgeOverlayState.Settling)

            fakeNow = StabilityTracker.DEFAULT_HOLD_MS + 50L
            viewModel.processFrame(createNonUniformBitmap())
            advanceUntilIdle()

            assertTrue(
                "After ≥1.5s of stability the overlay should be Locked",
                viewModel.overlayState.value is EdgeOverlayState.Locked,
            )
            // Lock fires exactly one auto-capture emit.
            awaitItem()
            cancelAndConsumeRemainingEvents()
        }
    }

    // ── Uniform bitmap → no quad → Idle/None overlay. ────────────────────────

    @Test
    fun processFrame_withUniformBitmap_transitionsToNone() = runTest {
        viewModel = newViewModel(FakeEdgeDetector())
        viewModel.processFrame(createUniformBitmap())
        advanceUntilIdle()
        assertEquals(EdgeOverlayState.None, viewModel.overlayState.value)
    }

    @Test
    fun markPartial_setsPartialState() = runTest {
        val mockDetector = mockk<EdgeDetector>()
        every { mockDetector.detect(any()) } returns DetectionResult.NotFound
        viewModel = newViewModel(mockDetector)
        viewModel.markPartial()
        assertEquals(EdgeOverlayState.Partial, viewModel.overlayState.value)
    }

    @Test
    fun processFrame_withNull_staysNone() = runTest {
        viewModel = newViewModel(FakeEdgeDetector())
        viewModel.processFrame(null)
        advanceUntilIdle()
        assertEquals(EdgeOverlayState.None, viewModel.overlayState.value)
    }

    // ── Moving the document resets the stability progress to 0. ──────────────

    @Test
    fun movingQuad_resetsToSettlingZero() = runTest {
        val quadA = Quad(
            topLeft = Point2f(10f, 10f),
            topRight = Point2f(90f, 10f),
            bottomRight = Point2f(90f, 90f),
            bottomLeft = Point2f(10f, 90f),
        )
        val quadFar = Quad(
            topLeft = Point2f(40f, 40f),
            topRight = Point2f(95f, 40f),
            bottomRight = Point2f(95f, 95f),
            bottomLeft = Point2f(40f, 95f),
        )
        val detector = mockk<EdgeDetector>()
        every { detector.detect(any()) } returnsMany listOf(
            DetectionResult.Found(quadA),
            DetectionResult.Found(quadFar),
        )
        viewModel = newViewModel(detector)

        viewModel.processFrame(createNonUniformBitmap())
        advanceUntilIdle()
        fakeNow = 500L
        viewModel.processFrame(createNonUniformBitmap())
        advanceUntilIdle()

        val state = viewModel.overlayState.value
        assertTrue(state is EdgeOverlayState.Settling)
        assertEquals(0f, (state as EdgeOverlayState.Settling).progress, 0.0001f)
    }

    // ── resetStability() clears the overlay back to None. ────────────────────

    @Test
    fun resetStability_returnsOverlayToNone() = runTest {
        viewModel = newViewModel(FakeEdgeDetector())
        viewModel.processFrame(createNonUniformBitmap())
        advanceUntilIdle()
        assertTrue(viewModel.overlayState.value is EdgeOverlayState.Settling)

        viewModel.resetStability()
        assertEquals(EdgeOverlayState.None, viewModel.overlayState.value)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun createUniformBitmap(): Bitmap =
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }

    private fun createNonUniformBitmap(): Bitmap =
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (y in 20 until 80) for (x in 20 until 80) setPixel(x, y, Color.BLACK)
        }
}
