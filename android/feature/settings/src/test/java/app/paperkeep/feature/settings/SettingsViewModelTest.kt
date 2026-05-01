package app.paperkeep.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import app.paperkeep.core.security.BiometricLockManager
import app.paperkeep.core.security.LockController
import app.paperkeep.core.security.LockTimeout
import app.paperkeep.core.ui.theme.AppTheme
import app.paperkeep.core.ui.theme.ThemePreferences
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
    private lateinit var themePreferences: ThemePreferences
    private lateinit var vm: SettingsViewModel

    private val lockEnabledFlow = MutableStateFlow(false)
    private val timeoutFlow = MutableStateFlow(LockTimeout.IMMEDIATE)
    private val appThemeFlow = MutableStateFlow(AppTheme.SYSTEM)
    private val oledFlow = MutableStateFlow(false)

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
        themePreferences = mockk(relaxed = true) {
            every { appTheme } returns appThemeFlow
            every { oledTrueBlack } returns oledFlow
        }
        vm = SettingsViewModel(context, biometricManager, lockController, themePreferences)
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

    // ── Dark mode (P4.8) ──────────────────────────────────────────────────────

    @Test
    fun `initial appTheme is SYSTEM`() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(AppTheme.SYSTEM, state.appTheme)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial oledTrueBlack is false`() = runTest {
        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.oledTrueBlack)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAppTheme calls themePreferences setAppTheme`() = runTest {
        vm.setAppTheme(AppTheme.DARK)
        advanceUntilIdle()
        coVerify { themePreferences.setAppTheme(AppTheme.DARK) }
    }

    @Test
    fun `setOledTrueBlack calls themePreferences setOledTrueBlack`() = runTest {
        vm.setOledTrueBlack(true)
        advanceUntilIdle()
        coVerify { themePreferences.setOledTrueBlack(true) }
    }

    @Test
    fun `appTheme from flow is reflected in uiState`() = runTest {
        vm.uiState.test {
            awaitItem() // initial

            appThemeFlow.value = AppTheme.DARK
            advanceUntilIdle()

            val state = awaitItem()
            assertEquals(AppTheme.DARK, state.appTheme)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `oledTrueBlack from flow is reflected in uiState`() = runTest {
        vm.uiState.test {
            awaitItem() // initial

            oledFlow.value = true
            advanceUntilIdle()

            val state = awaitItem()
            assertTrue(state.oledTrueBlack)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
