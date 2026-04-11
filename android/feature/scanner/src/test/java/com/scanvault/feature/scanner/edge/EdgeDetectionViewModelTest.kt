package com.scanvault.feature.scanner.edge

import android.graphics.Bitmap
import android.graphics.Color
import com.scanvault.core.common.AppDispatchers
import com.scanvault.core.imaging.DetectionResult
import com.scanvault.core.imaging.EdgeDetector
import com.scanvault.core.imaging.FakeEdgeDetector
import com.scanvault.core.imaging.Point2f
import com.scanvault.core.imaging.Quad
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for 1B.11 + 1B.12: EdgeDetectionViewModel and overlay state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EdgeDetectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: EdgeDetectionViewModel

    /** Fake AppDispatchers that routes everything to testDispatcher */
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

    // ── 1B.12: overlay renders green when corners detected ────────────────────

    @Test
    fun processFrame_withDocumentBitmap_transitionsToGood() = runTest {
        viewModel = EdgeDetectionViewModel(FakeEdgeDetector(), testDispatchers)

        viewModel.processFrame(createNonUniformBitmap())
        advanceUntilIdle()

        assertTrue(
            "State must be Good when detector finds corners",
            viewModel.overlayState.value is EdgeOverlayState.Good
        )
    }

    // ── 1B.12: overlay invisible when no detection ────────────────────────────

    @Test
    fun processFrame_withUniformBitmap_transitionsToNone() = runTest {
        viewModel = EdgeDetectionViewModel(FakeEdgeDetector(), testDispatchers)

        viewModel.processFrame(createUniformBitmap())
        advanceUntilIdle()

        assertEquals(EdgeOverlayState.None, viewModel.overlayState.value)
    }

    // ── 1B.12: overlay renders amber when partial ──────────────────────────────

    @Test
    fun markPartial_setsPartialState() = runTest {
        val mockDetector = mockk<EdgeDetector>()
        every { mockDetector.detect(any()) } returns DetectionResult.NotFound
        viewModel = EdgeDetectionViewModel(mockDetector, testDispatchers)

        viewModel.markPartial()

        assertEquals(EdgeOverlayState.Partial, viewModel.overlayState.value)
    }

    // ── 1B.11: null bitmap returns None ───────────────────────────────────────

    @Test
    fun processFrame_withNull_staysNone() = runTest {
        viewModel = EdgeDetectionViewModel(FakeEdgeDetector(), testDispatchers)

        viewModel.processFrame(null)
        advanceUntilIdle()

        assertEquals(EdgeOverlayState.None, viewModel.overlayState.value)
    }

    // ── 1B.12: animation state transitions ────────────────────────────────────

    @Test
    fun overlayState_transitionsFromGoodToNone_afterUniformFrame() = runTest {
        viewModel = EdgeDetectionViewModel(FakeEdgeDetector(), testDispatchers)

        // First frame: document detected → Good
        viewModel.processFrame(createNonUniformBitmap())
        advanceUntilIdle()
        assertTrue(viewModel.overlayState.value is EdgeOverlayState.Good)

        // Second frame: no document → None
        viewModel.processFrame(createUniformBitmap())
        advanceUntilIdle()
        assertEquals(EdgeOverlayState.None, viewModel.overlayState.value)
    }

    // ── 1B.11: Good state contains the detected quad ──────────────────────────

    @Test
    fun processFrame_goodState_containsQuadFromDetector() = runTest {
        val expectedQuad = Quad(
            topLeft = Point2f(10f, 10f),
            topRight = Point2f(90f, 10f),
            bottomRight = Point2f(90f, 90f),
            bottomLeft = Point2f(10f, 90f),
        )
        val mockDetector = mockk<EdgeDetector>()
        every { mockDetector.detect(any()) } returns DetectionResult.Found(expectedQuad)
        viewModel = EdgeDetectionViewModel(mockDetector, testDispatchers)

        viewModel.processFrame(createNonUniformBitmap())
        advanceUntilIdle()

        val state = viewModel.overlayState.value
        assertTrue(state is EdgeOverlayState.Good)
        assertEquals(expectedQuad, (state as EdgeOverlayState.Good).quad)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun createUniformBitmap(): Bitmap =
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }

    private fun createNonUniformBitmap(): Bitmap =
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            // Dark patch — makes it non-uniform
            for (y in 20 until 80) for (x in 20 until 80) setPixel(x, y, Color.BLACK)
        }
}
