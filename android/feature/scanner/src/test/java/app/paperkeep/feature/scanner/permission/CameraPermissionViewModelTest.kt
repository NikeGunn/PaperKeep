package app.paperkeep.feature.scanner.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for 1B.8: Camera permissions state machine.
 * All tests run via CameraPermissionViewModel — no Android framework needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraPermissionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CameraPermissionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CameraPermissionViewModel()
        mockkStatic(ContextCompat::class)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(ContextCompat::class)
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun initialState_isNotRequested() = runTest {
        assertEquals(CameraPermissionState.NotRequested, viewModel.state.value)
    }

    // ── checkInitialPermission ─────────────────────────────────────────────────

    @Test
    fun checkInitialPermission_whenAlreadyGranted_transitionsToGranted() = runTest {
        val context = mockk<Context>()
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        } returns PackageManager.PERMISSION_GRANTED

        viewModel.checkInitialPermission(context)

        assertEquals(CameraPermissionState.Granted, viewModel.state.value)
    }

    @Test
    fun checkInitialPermission_whenDenied_staysNotRequested() = runTest {
        val context = mockk<Context>()
        every {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        } returns PackageManager.PERMISSION_DENIED

        viewModel.checkInitialPermission(context)

        assertEquals(CameraPermissionState.NotRequested, viewModel.state.value)
    }

    // ── onPermissionResult ─────────────────────────────────────────────────────

    @Test
    fun onPermissionResult_whenGranted_transitionsToGranted() = runTest {
        viewModel.onPermissionResult(granted = true, canShowRationale = false)

        assertEquals(CameraPermissionState.Granted, viewModel.state.value)
    }

    @Test
    fun onPermissionResult_whenDeniedAndCanShowRationale_transitionsToRationale() = runTest {
        viewModel.onPermissionResult(granted = false, canShowRationale = true)

        assertEquals(CameraPermissionState.ShowRationale, viewModel.state.value)
    }

    @Test
    fun onPermissionResult_whenPermanentlyDenied_transitionsToPermanentDenial() = runTest {
        viewModel.onPermissionResult(granted = false, canShowRationale = false)

        assertEquals(CameraPermissionState.PermanentlyDenied, viewModel.state.value)
    }

    // ── showRationale ──────────────────────────────────────────────────────────

    @Test
    fun showRationale_fromNotRequested_transitionsToShowRationale() = runTest {
        viewModel.showRationale()

        assertEquals(CameraPermissionState.ShowRationale, viewModel.state.value)
    }

    @Test
    fun showRationale_fromGranted_doesNothing() = runTest {
        viewModel.onPermissionResult(granted = true, canShowRationale = false)
        viewModel.showRationale()

        // Granted state should be preserved — rationale shouldn't re-show once granted
        assertEquals(CameraPermissionState.Granted, viewModel.state.value)
    }

    // ── State transitions — full permission flow ───────────────────────────────

    @Test
    fun permissionFlow_deniedThenGranted_endsInGranted() = runTest {
        // User denies first, then grants on re-request
        viewModel.onPermissionResult(granted = false, canShowRationale = true)
        assertEquals(CameraPermissionState.ShowRationale, viewModel.state.value)

        viewModel.onPermissionResult(granted = true, canShowRationale = false)
        assertEquals(CameraPermissionState.Granted, viewModel.state.value)
    }

    @Test
    fun permissionFlow_deniedTwiceBecomesPermanent() = runTest {
        viewModel.onPermissionResult(granted = false, canShowRationale = true)
        assertEquals(CameraPermissionState.ShowRationale, viewModel.state.value)

        viewModel.onPermissionResult(granted = false, canShowRationale = false)
        assertEquals(CameraPermissionState.PermanentlyDenied, viewModel.state.value)
    }
}
