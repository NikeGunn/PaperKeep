package app.paperkeep.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [BiometricLockManager] — Task 2B.11.
 *
 * Tests:
 *  1. isLockEnabled defaults to false
 *  2. setLockEnabled(true) persists and is reflected in the flow
 *  3. setLockEnabled(false) persists and disables lock
 *  4. isBiometricAvailable returns true when BIOMETRIC_SUCCESS
 *  5. isBiometricAvailable returns true when BIOMETRIC_ERROR_NONE_ENROLLED (hw present, not enrolled)
 *  6. isBiometricAvailable returns false when hardware unavailable
 *  7. showPrompt calls onFailure immediately when no hardware
 *  8. isStrongBiometricEnrolled returns correct values
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BiometricLockManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var manager: BiometricLockManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        manager = BiometricLockManager(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ── 2B.11: isLockEnabled defaults to false ────────────────────────────────

    @Test
    fun `isLockEnabled defaults to false before any preference is set`() = runTest {
        val enabled = manager.isLockEnabled.first()
        assertFalse("Lock should be disabled by default", enabled)
    }

    // ── 2B.11: setLockEnabled persists preference ─────────────────────────────

    @Test
    fun `setLockEnabled true persists and flow reflects true`() = runTest {
        manager.setLockEnabled(true)
        val enabled = manager.isLockEnabled.first()
        assertTrue("Lock should be enabled after setLockEnabled(true)", enabled)
    }

    @Test
    fun `setLockEnabled false after true persists and flow reflects false`() = runTest {
        manager.setLockEnabled(true)
        manager.setLockEnabled(false)
        val enabled = manager.isLockEnabled.first()
        assertFalse("Lock should be disabled after setLockEnabled(false)", enabled)
    }

    // ── 2B.11: isBiometricAvailable hardware check ────────────────────────────

    @Test
    fun `isBiometricAvailable returns true when canAuthenticate returns BIOMETRIC_SUCCESS`() {
        // Under Robolectric, BiometricManager.from() returns a shadow that defaults to
        // BIOMETRIC_ERROR_NO_HARDWARE. We mock the static factory to control the result.
        val biometricManager = mockk<BiometricManager>()
        every {
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } returns BiometricManager.BIOMETRIC_SUCCESS

        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns biometricManager

        assertTrue(manager.isBiometricAvailable())
    }

    @Test
    fun `isBiometricAvailable returns true when hardware present but none enrolled`() {
        val biometricManager = mockk<BiometricManager>()
        every {
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns biometricManager

        assertTrue(
            "HW present but not enrolled should still allow DEVICE_CREDENTIAL fallback",
            manager.isBiometricAvailable()
        )
    }

    @Test
    fun `isBiometricAvailable returns false when hardware unavailable`() {
        val biometricManager = mockk<BiometricManager>()
        every {
            biometricManager.canAuthenticate(any())
        } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns biometricManager

        assertFalse("No hardware should return false", manager.isBiometricAvailable())
    }

    // ── 2B.11: showPrompt calls onFailure when hardware unavailable ───────────

    @Test
    fun `showPrompt calls onFailure immediately when no biometric hardware`() {
        val biometricManager = mockk<BiometricManager>()
        every { biometricManager.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns biometricManager

        var failureCalled = false
        var successCalled = false

        // We can't supply a real FragmentActivity in a unit test (Robolectric would
        // provide it), but since isBiometricAvailable() is false the method short-circuits
        // before touching the activity parameter — so null is safe here.
        manager.showPrompt(
            activity = mockk(relaxed = true),
            onSuccess = { successCalled = true },
            onFailure = { failureCalled = true },
        )

        assertTrue("onFailure must be called when no hardware", failureCalled)
        assertFalse("onSuccess must NOT be called", successCalled)
    }

    // ── 2B.11: isStrongBiometricEnrolled ─────────────────────────────────────

    @Test
    fun `isStrongBiometricEnrolled returns true when BIOMETRIC_SUCCESS for BIOMETRIC_STRONG`() {
        val biometricManager = mockk<BiometricManager>()
        every {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_SUCCESS

        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns biometricManager

        assertTrue(manager.isStrongBiometricEnrolled())
    }

    @Test
    fun `isStrongBiometricEnrolled returns false when no strong biometric enrolled`() {
        val biometricManager = mockk<BiometricManager>()
        every {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(any()) } returns biometricManager

        assertFalse(manager.isStrongBiometricEnrolled())
    }
}
