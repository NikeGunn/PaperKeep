package app.paperkeep.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import app.paperkeep.core.security.BiometricLockManager
import app.paperkeep.core.security.LockController
import app.paperkeep.core.security.LockTimeout
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var biometricManager: BiometricLockManager
    private lateinit var lockController: LockController
    private lateinit var vm: SettingsViewModel

    private val lockEnabledFlow = MutableStateFlow(false)
    private val timeoutFlow = MutableStateFlow(LockTimeout.IMMEDIATE)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        biometricManager = mockk(relaxed = true) {
            every { isLockEnabled } returns lockEnabledFlow
            every { isBiometricAvailable() } returns true
        }
        lockController = mockk(relaxed = true) {
            every { lockTimeout } returns timeoutFlow
        }
        vm = SettingsViewModel(context, biometricManager, lockController)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has biometricLockEnabled false`() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.biometricLockEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state has screenshotProtection true`() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.screenshotProtectionEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state reflects isBiometricAvailable from manager`() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isBiometricAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── setBiometricLock ──────────────────────────────────────────────────────

    @Test
    fun `setBiometricLock true calls biometricManager setLockEnabled`() = runTest {
        vm.setBiometricLock(true)
        advanceUntilIdle()
        coVerify { biometricManager.setLockEnabled(true) }
    }

    @Test
    fun `setBiometricLock false calls setLockEnabled false and lockNow`() = runTest {
        vm.setBiometricLock(false)
        advanceUntilIdle()
        coVerify { biometricManager.setLockEnabled(false) }
        coVerify { lockController.lockNow() }
    }

    // ── setLockTimeout ────────────────────────────────────────────────────────

    @Test
    fun `setLockTimeout calls lockController setLockTimeout`() = runTest {
        vm.setLockTimeout(LockTimeout.FIVE_MINUTES)
        advanceUntilIdle()
        coVerify { lockController.setLockTimeout(LockTimeout.FIVE_MINUTES) }
    }

    // ── setScreenshotProtection ───────────────────────────────────────────────

    @Test
    fun `setScreenshotProtection false updates state`() = runTest {
        vm.uiState.test {
            awaitItem() // initial

            vm.setScreenshotProtection(false)
            advanceUntilIdle()

            val state = awaitItem()
            assertFalse(state.screenshotProtectionEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setScreenshotProtection true restores state`() = runTest {
        vm.setScreenshotProtection(false)
        vm.setScreenshotProtection(true)
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.screenshotProtectionEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── lockTimeout reflected in state ────────────────────────────────────────

    @Test
    fun `lockTimeout from flow reflected in uiState`() = runTest {
        vm.uiState.test {
            awaitItem() // initial IMMEDIATE

            timeoutFlow.value = LockTimeout.FIVE_MINUTES
            advanceUntilIdle()

            val state = awaitItem()
            assertEquals(LockTimeout.FIVE_MINUTES, state.lockTimeout)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── biometricLockEnabled reflected in state ────────────────────────────────

    @Test
    fun `biometricLockEnabled from flow reflected in uiState`() = runTest {
        vm.uiState.test {
            awaitItem() // initial false

            lockEnabledFlow.value = true
            advanceUntilIdle()

            val state = awaitItem()
            assertTrue(state.biometricLockEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
