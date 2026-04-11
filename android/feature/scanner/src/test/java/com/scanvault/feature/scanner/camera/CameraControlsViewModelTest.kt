package com.scanvault.feature.scanner.camera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

/**
 * Unit tests for 1B.10: Camera controls (torch, grid, zoom, tap-to-focus).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraControlsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CameraControlsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CameraControlsViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun initialState_torchIsOff() = runTest {
        assertFalse(viewModel.state.value.isTorchOn)
    }

    @Test
    fun initialState_gridIsHidden() = runTest {
        assertFalse(viewModel.state.value.isGridVisible)
    }

    @Test
    fun initialState_zoomIsOne() = runTest {
        assertEquals(1.0f, viewModel.state.value.zoomRatio, 0.001f)
    }

    @Test
    fun initialState_focusPointIsNull() = runTest {
        assertNull(viewModel.state.value.focusPoint)
    }

    // ── Torch toggle ───────────────────────────────────────────────────────────

    @Test
    fun toggleTorch_turnsOn() = runTest {
        viewModel.toggleTorch()
        assertTrue(viewModel.state.value.isTorchOn)
    }

    @Test
    fun toggleTorch_twiceReturnsFalse() = runTest {
        viewModel.toggleTorch()
        viewModel.toggleTorch()
        assertFalse(viewModel.state.value.isTorchOn)
    }

    // ── Grid overlay ───────────────────────────────────────────────────────────

    @Test
    fun toggleGrid_showsGrid() = runTest {
        viewModel.toggleGrid()
        assertTrue(viewModel.state.value.isGridVisible)
    }

    @Test
    fun toggleGrid_hidesGridOnSecondTap() = runTest {
        viewModel.toggleGrid()
        viewModel.toggleGrid()
        assertFalse(viewModel.state.value.isGridVisible)
    }

    // ── Zoom (pinch-to-zoom) ───────────────────────────────────────────────────

    @Test
    fun onZoomChanged_setsZoomRatio() = runTest {
        viewModel.onZoomChanged(2.0f)
        assertEquals(2.0f, viewModel.state.value.zoomRatio, 0.001f)
    }

    @Test
    fun onZoomChanged_clampsToMaxZoom() = runTest {
        viewModel.onZoomChanged(10.0f)
        val max = viewModel.state.value.maxZoom
        assertEquals(max, viewModel.state.value.zoomRatio, 0.001f)
    }

    @Test
    fun onZoomChanged_clampsToMinZoom() = runTest {
        viewModel.onZoomChanged(0.1f)
        val min = viewModel.state.value.minZoom
        assertEquals(min, viewModel.state.value.zoomRatio, 0.001f)
    }

    @Test
    fun setZoomBounds_updatesMinAndMax() = runTest {
        viewModel.setZoomBounds(min = 0.5f, max = 5.0f)
        val state = viewModel.state.value
        assertEquals(0.5f, state.minZoom, 0.001f)
        assertEquals(5.0f, state.maxZoom, 0.001f)
    }

    // ── Tap-to-focus ───────────────────────────────────────────────────────────

    @Test
    fun onTapToFocus_setsFocusPoint() = runTest {
        viewModel.onTapToFocus(300f, 400f)
        val fp = viewModel.state.value.focusPoint
        assertNotNull(fp)
        assertEquals(300f, fp!!.x, 0.001f)
        assertEquals(400f, fp.y, 0.001f)
    }

    @Test
    fun clearFocusPoint_removesPoint() = runTest {
        viewModel.onTapToFocus(100f, 200f)
        viewModel.clearFocusPoint()
        assertNull(viewModel.state.value.focusPoint)
    }

    @Test
    fun onTapToFocus_updatesPoint_whenTappedAgain() = runTest {
        viewModel.onTapToFocus(100f, 100f)
        viewModel.onTapToFocus(500f, 600f)
        val fp = viewModel.state.value.focusPoint
        assertEquals(500f, fp!!.x, 0.001f)
        assertEquals(600f, fp.y, 0.001f)
    }

    // ── Touch target size contract ─────────────────────────────────────────────

    @Test
    fun minTouchTarget_isAtLeast48dp() {
        // MIN_TOUCH_TARGET constant must be ≥ 48dp per Material 3 / WCAG 2.5.5
        assertTrue(
            "MIN_TOUCH_TARGET must be >= 48dp, got $MIN_TOUCH_TARGET",
            MIN_TOUCH_TARGET.value >= 48f
        )
    }

    // ── ContentDescription constants ───────────────────────────────────────────

    @Test
    fun testTags_areNonEmpty() {
        assertTrue(TAG_TORCH_BUTTON.isNotBlank())
        assertTrue(TAG_GRID_BUTTON.isNotBlank())
        assertTrue(TAG_CAPTURE_BUTTON.isNotBlank())
        assertTrue(TAG_GRID_OVERLAY.isNotBlank())
        assertTrue(TAG_FOCUS_RING.isNotBlank())
    }
}
