package app.paperkeep.feature.scanner.idcard

import android.graphics.Bitmap
import android.graphics.Color
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.feature.scanner.IdCardCaptureMode
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class IdCardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : AppDispatchers {
        override val main    = testDispatcher
        override val io      = testDispatcher
        override val default = testDispatcher
    }

    @Before fun setUp()    { Dispatchers.setMain(testDispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state is AwaitingFront`() = runTest {
        val vm = buildVm()
        assertTrue(vm.state.value is IdCardFlowState.AwaitingFront)
    }

    // ── Front confirmed ───────────────────────────────────────────────────────

    @Test
    fun `onFrontConfirmed transitions to AwaitingBack`() = runTest {
        val vm = buildVm()
        vm.onFrontConfirmed(whiteBitmap())
        assertTrue(vm.state.value is IdCardFlowState.AwaitingBack)
    }

    @Test
    fun `AwaitingBack carries front preview bitmap`() = runTest {
        val front = whiteBitmap()
        val vm = buildVm()
        vm.onFrontConfirmed(front)
        val state = vm.state.value as IdCardFlowState.AwaitingBack
        assertNotNull(state.frontPreview)
        assertEquals(front.width, state.frontPreview.width)
    }

    // ── Back confirmed → Done ─────────────────────────────────────────────────

    @Test
    fun `onBackConfirmed after front transitions to Done`() = runTest {
        val vm = buildVm()
        vm.onFrontConfirmed(whiteBitmap())
        vm.onBackConfirmed(whiteBitmap())
        advanceUntilIdle()
        assertTrue(vm.state.value is IdCardFlowState.Done)
    }

    @Test
    fun `Done state composite is A4 dimensions`() = runTest {
        val vm = buildVm()
        vm.onFrontConfirmed(whiteBitmap())
        vm.onBackConfirmed(whiteBitmap())
        advanceUntilIdle()
        val done = vm.state.value as IdCardFlowState.Done
        assertEquals(IdCardCaptureMode.A4_W, done.composite.width)
        assertEquals(IdCardCaptureMode.A4_H, done.composite.height)
    }

    // ── Retake front ──────────────────────────────────────────────────────────

    @Test
    fun `retakeFront resets to AwaitingFront`() = runTest {
        val vm = buildVm()
        vm.onFrontConfirmed(whiteBitmap())
        vm.retakeFront()
        assertTrue(vm.state.value is IdCardFlowState.AwaitingFront)
    }

    // ── Full reset ────────────────────────────────────────────────────────────

    @Test
    fun `reset from Done returns to AwaitingFront`() = runTest {
        val vm = buildVm()
        vm.onFrontConfirmed(whiteBitmap())
        vm.onBackConfirmed(whiteBitmap())
        advanceUntilIdle()
        vm.reset()
        assertTrue(vm.state.value is IdCardFlowState.AwaitingFront)
    }

    @Test
    fun `reset from AwaitingBack returns to AwaitingFront`() = runTest {
        val vm = buildVm()
        vm.onFrontConfirmed(whiteBitmap())
        vm.reset()
        assertTrue(vm.state.value is IdCardFlowState.AwaitingFront)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildVm() = IdCardViewModel(IdCardCaptureMode(), testDispatchers)

    private fun whiteBitmap(): Bitmap =
        Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE) }
}
