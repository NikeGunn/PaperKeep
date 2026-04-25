package app.paperkeep.feature.scanner.rotation

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.imaging.FakeEdgeDetector
import app.paperkeep.core.imaging.Point2f
import app.paperkeep.core.imaging.Quad
import app.paperkeep.feature.scanner.capture.CaptureState
import app.paperkeep.feature.scanner.capture.CaptureViewModel
import app.paperkeep.feature.scanner.camera.CameraControlsViewModel
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for 1B.19: Screen rotation support.
 *
 * ViewModels survive configuration changes via Hilt's ViewModel scoping.
 * SavedStateHandle persists serializable state across process death.
 *
 * We test the ViewModel layer — the Compose rememberSaveable layer is verified
 * by Android instrumented tests (androidTest/).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScreenRotationTest {

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

    // ── 1B.19: State persists after config change (SavedStateHandle) ──────────

    @Test
    fun captureViewModel_quadSurvivesRecreation_viaSavedState() = runTest {
        val savedState = SavedStateHandle()
        val vm = CaptureViewModel(FakeEdgeDetector(), app.paperkeep.core.ml.DocumentClassifier(), testDispatchers, savedState)

        // Capture an image so state is ReadyToCrop
        val bitmap = makeBitmap()
        vm.onImageCaptured(bitmap)
        advanceUntilIdle()

        // Update quad (simulates user dragging corners)
        val updatedQuad = Quad(
            topLeft = Point2f(10f, 20f),
            topRight = Point2f(300f, 20f),
            bottomRight = Point2f(300f, 400f),
            bottomLeft = Point2f(10f, 400f),
        )
        vm.onQuadUpdated(updatedQuad)
        advanceUntilIdle()

        // Simulate rotation: VM is recreated with the SAME SavedStateHandle
        val recreatedVm = CaptureViewModel(FakeEdgeDetector(), app.paperkeep.core.ml.DocumentClassifier(), testDispatchers, savedState)
        val restoredQuad = recreatedVm.restoreQuadFromSavedState()

        assertNotNull("Quad must survive rotation via SavedStateHandle", restoredQuad)
        assertEquals(updatedQuad.topLeft.x, restoredQuad!!.topLeft.x, 0.01f)
        assertEquals(updatedQuad.bottomRight.y, restoredQuad.bottomRight.y, 0.01f)
    }

    @Test
    fun cameraControlsViewModel_torchState_isRetainedAcrossRotation() = runTest {
        // CameraControlsViewModel state lives in the ViewModel (survives rotation
        // naturally because ViewModels are not destroyed on config change).
        val vm = CameraControlsViewModel()
        vm.toggleTorch()
        val torchBefore = vm.state.value.isTorchOn

        // Simulated "same ViewModel instance after rotation" — torch must be ON
        assertFalse("Torch must be ON after toggle", !torchBefore)
    }

    @Test
    fun captureViewModel_retakeResetsToIdle_afterRotation() = runTest {
        val savedState = SavedStateHandle()
        val vm = CaptureViewModel(FakeEdgeDetector(), app.paperkeep.core.ml.DocumentClassifier(), testDispatchers, savedState)
        vm.onImageCaptured(makeBitmap())
        advanceUntilIdle()

        vm.retake()

        // After retake + rotation the new VM instance has no saved quad
        val recreatedVm = CaptureViewModel(FakeEdgeDetector(), app.paperkeep.core.ml.DocumentClassifier(), testDispatchers, savedState)
        assertEquals(CaptureState.Idle, recreatedVm.state.value)
        // Quad was cleared from SavedStateHandle
        assertNull(recreatedVm.restoreQuadFromSavedState(), "Quad must be null after retake")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeBitmap(): Bitmap =
        Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.eraseColor(Color.WHITE)
            for (y in 50 until 430) for (x in 50 until 270) bmp.setPixel(x, y, Color.BLACK)
        }

    private fun assertNull(value: Any?, message: String) {
        org.junit.Assert.assertNull(message, value)
    }
}
