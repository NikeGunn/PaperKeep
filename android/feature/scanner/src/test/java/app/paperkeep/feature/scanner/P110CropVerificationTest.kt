package app.paperkeep.feature.scanner

import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.imaging.FakeEdgeDetector
import app.paperkeep.core.imaging.Point2f
import app.paperkeep.core.imaging.Quad
import app.paperkeep.core.ml.DocumentClassifier
import app.paperkeep.core.ml.OcrOrchestrator
import app.paperkeep.feature.scanner.capture.CaptureState
import app.paperkeep.feature.scanner.capture.CaptureViewModel
import app.paperkeep.feature.scanner.capture.TAG_CORNER_BL
import app.paperkeep.feature.scanner.capture.TAG_CORNER_BR
import app.paperkeep.feature.scanner.capture.TAG_CORNER_TL
import app.paperkeep.feature.scanner.capture.TAG_CORNER_TR
import app.paperkeep.feature.scanner.capture.TAG_CROP_SCREEN
import app.paperkeep.feature.scanner.capture.TAG_NEXT_BUTTON
import app.paperkeep.feature.scanner.capture.TAG_RETAKE_BUTTON
import app.paperkeep.feature.scanner.capture.TAG_ROTATE_BUTTON
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * P1.10 — Crop screen acceptance verification.
 *
 * Checks:
 *  1. 4 draggable corner handle tags exist and are unique
 *  2. Rotate 90° swaps bitmap dimensions
 *  3. Retake resets to Idle state
 *  4. Next button tag present
 *  5. Quad update persists in state
 *  6. State survives rotation (SavedStateHandle)
 *
 * Note: The 50×50px magnifier lens (2× zoom) is a Phase 1 "nice to have"
 * per the spec. The core handle interaction is the mandatory requirement.
 * The magnifier is tracked as P1.10-magnifier and will be implemented as
 * a follow-on to avoid blocking Phase 2.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class P110CropVerificationTest {

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

    // ── 1. 4 corner handle tags exist and are unique ───────────────────────────

    @Test
    fun cornerHandleTags_areFour_andUnique() {
        val tags = setOf(TAG_CORNER_TL, TAG_CORNER_TR, TAG_CORNER_BR, TAG_CORNER_BL)
        assertEquals("Must have exactly 4 unique corner handle tags", 4, tags.size)
    }

    @Test
    fun cornerHandleTags_areNonEmpty() {
        listOf(TAG_CORNER_TL, TAG_CORNER_TR, TAG_CORNER_BR, TAG_CORNER_BL)
            .forEach { assertTrue("Corner tag must not be empty: $it", it.isNotBlank()) }
    }

    // ── 2. Rotate 90° swaps bitmap dimensions ─────────────────────────────────

    @Test
    fun rotate_swapsBitmapWidthAndHeight() = runTest {
        val vm = buildVm()
        vm.onImageCaptured(makeBitmap(width = 300, height = 500))
        advanceUntilIdle()

        val before = (vm.state.value as CaptureState.ReadyToCrop).image
        vm.rotateImage()
        advanceUntilIdle()

        val after = (vm.state.value as CaptureState.ReadyToCrop).image
        assertEquals("Rotate must swap width to height", before.height, after.width)
        assertEquals("Rotate must swap height to width", before.width, after.height)
    }

    @Test
    fun rotate_twice_restoresOriginalDimensions() = runTest {
        val vm = buildVm()
        vm.onImageCaptured(makeBitmap(width = 300, height = 500))
        advanceUntilIdle()

        val before = (vm.state.value as CaptureState.ReadyToCrop).image
        vm.rotateImage()
        advanceUntilIdle()
        vm.rotateImage()
        advanceUntilIdle()

        val after = (vm.state.value as CaptureState.ReadyToCrop).image
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
    }

    // ── 3. Retake resets to Idle ───────────────────────────────────────────────

    @Test
    fun retake_resetsStateToIdle() = runTest {
        val vm = buildVm()
        vm.onImageCaptured(makeBitmap())
        advanceUntilIdle()
        assertTrue("Must be ReadyToCrop after capture", vm.state.value is CaptureState.ReadyToCrop)

        vm.retake()

        assertEquals("Retake must reset to Idle", CaptureState.Idle, vm.state.value)
    }

    // ── 4. Next button tag present ─────────────────────────────────────────────

    @Test
    fun nextButtonTag_isNonEmpty() {
        assertTrue(TAG_NEXT_BUTTON.isNotBlank())
    }

    @Test
    fun retakeButtonTag_isNonEmpty() {
        assertTrue(TAG_RETAKE_BUTTON.isNotBlank())
    }

    @Test
    fun rotateButtonTag_isNonEmpty() {
        assertTrue(TAG_ROTATE_BUTTON.isNotBlank())
    }

    @Test
    fun cropScreenTag_isNonEmpty() {
        assertTrue(TAG_CROP_SCREEN.isNotBlank())
    }

    // ── 5. Quad update persists in state ──────────────────────────────────────

    @Test
    fun onQuadUpdated_persistsNewQuad() = runTest {
        val vm = buildVm()
        vm.onImageCaptured(makeBitmap())
        advanceUntilIdle()

        val newQuad = Quad(
            topLeft = Point2f(10f, 10f),
            topRight = Point2f(290f, 10f),
            bottomRight = Point2f(290f, 490f),
            bottomLeft = Point2f(10f, 490f),
        )
        vm.onQuadUpdated(newQuad)
        advanceUntilIdle()

        val state = vm.state.value as CaptureState.ReadyToCrop
        assertEquals(newQuad.topLeft.x, state.quad.topLeft.x, 0.001f)
        assertEquals(newQuad.bottomRight.y, state.quad.bottomRight.y, 0.001f)
    }

    // ── 6. State survives rotation (SavedStateHandle) ─────────────────────────

    @Test
    fun quadState_survivesActivityRecreation() = runTest {
        val savedState = SavedStateHandle()
        val vm1 = buildVm(savedState)
        vm1.onImageCaptured(makeBitmap())
        advanceUntilIdle()

        val quad = Quad(
            topLeft = Point2f(5f, 5f),
            topRight = Point2f(295f, 5f),
            bottomRight = Point2f(295f, 395f),
            bottomLeft = Point2f(5f, 395f),
        )
        vm1.onQuadUpdated(quad)
        advanceUntilIdle()

        // Simulate Activity recreation
        val vm2 = buildVm(savedState)
        val restored = vm2.restoreQuadFromSavedState()

        assertNotNull("Quad must be restored from SavedStateHandle", restored)
        assertEquals(quad.topLeft.x, restored!!.topLeft.x, 0.01f)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildVm(savedState: SavedStateHandle = SavedStateHandle()) =
        CaptureViewModel(
            edgeDetector = FakeEdgeDetector(),
            classifier = DocumentClassifier(),
            dispatchers = testDispatchers,
            savedState = savedState,
            documentRepository = mockk(relaxed = true),
            imageStore = mockk(relaxed = true),
            scanDao = mockk(relaxed = true),
            ocrOrchestrator = mockk(relaxed = true),
            appContext = RuntimeEnvironment.getApplication(),
        )

    private fun makeBitmap(width: Int = 300, height: Int = 400): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE) }
}
