package app.paperkeep.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LockControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var biometricManager: BiometricLockManager
    private lateinit var lockEnabledFlow: MutableStateFlow<Boolean>
    private lateinit var controller: LockController

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        lockEnabledFlow = MutableStateFlow(false)
        biometricManager = mockk(relaxed = true) {
            every { isLockEnabled } returns lockEnabledFlow
        }
        controller = LockController(context, biometricManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── isLocked — lock disabled ───────────────────────────────────────────────

    @Test
    fun `isLocked is false when biometric lock is disabled`() = runTest {
        lockEnabledFlow.value = false
        val locked = controller.isLocked.first()
        assertFalse("App should not be locked when biometric lock is disabled", locked)
    }

    // ── isLocked — lock enabled, not unlocked ─────────────────────────────────

    @Test
    fun `isLocked is true when lock enabled and never unlocked`() = runTest {
        lockEnabledFlow.value = true
        val locked = controller.isLocked.first()
        assertTrue("App should be locked when never unlocked in this process", locked)
    }

    // ── onUnlocked ────────────────────────────────────────────────────────────

    @Test
    fun `isLocked is false after onUnlocked with IMMEDIATE timeout — stays open during session`() = runTest {
        lockEnabledFlow.value = true
        controller.setLockTimeout(LockTimeout.IMMEDIATE)
        controller.onUnlocked()

        // IMMEDIATE means "lock when app goes to background", NOT "lock every frame".
        // The app must stay unlocked while the session is active.
        val locked = controller.isLocked.first()
        assertFalse("App should stay unlocked during an active IMMEDIATE session", locked)
    }

    @Test
    fun `onAppBackground with IMMEDIATE timeout re-locks the app`() = runTest {
        lockEnabledFlow.value = true
        controller.setLockTimeout(LockTimeout.IMMEDIATE)
        controller.onUnlocked()

        controller.onAppBackground()
        val locked = controller.isLocked.first()
        assertTrue("App must re-lock after going to background with IMMEDIATE timeout", locked)
    }

    @Test
    fun `isLocked is false after onUnlocked with FIVE_MINUTES timeout`() = runTest {
        lockEnabledFlow.value = true
        controller.setLockTimeout(LockTimeout.FIVE_MINUTES)

        // Unlock NOW — elapsed will be ~0ms, well under 5 minutes
        controller.onUnlocked()

        val locked = controller.isLocked.first()
        assertFalse("Should not be locked within 5-minute timeout window", locked)
    }

    // ── lockNow ───────────────────────────────────────────────────────────────

    @Test
    fun `lockNow forces locked state even after unlock`() = runTest {
        lockEnabledFlow.value = true
        controller.setLockTimeout(LockTimeout.FIVE_MINUTES)
        controller.onUnlocked()

        controller.lockNow()

        val locked = controller.isLocked.first()
        assertTrue("lockNow() must force re-lock", locked)
    }

    // ── setLockTimeout ────────────────────────────────────────────────────────

    @Test
    fun `setLockTimeout persists and reads back THIRTY_SECONDS`() = runTest {
        controller.setLockTimeout(LockTimeout.THIRTY_SECONDS)
        val timeout = controller.lockTimeout.first()
        assert(timeout == LockTimeout.THIRTY_SECONDS) { "Expected THIRTY_SECONDS, got $timeout" }
    }

    @Test
    fun `setLockTimeout persists and reads back ONE_MINUTE`() = runTest {
        controller.setLockTimeout(LockTimeout.ONE_MINUTE)
        val timeout = controller.lockTimeout.first()
        assert(timeout == LockTimeout.ONE_MINUTE) { "Expected ONE_MINUTE, got $timeout" }
    }

    @Test
    fun `setLockTimeout persists and reads back FIVE_MINUTES`() = runTest {
        controller.setLockTimeout(LockTimeout.FIVE_MINUTES)
        val timeout = controller.lockTimeout.first()
        assert(timeout == LockTimeout.FIVE_MINUTES) { "Expected FIVE_MINUTES, got $timeout" }
    }

    // ── LockTimeout enum ──────────────────────────────────────────────────────

    @Test
    fun `LockTimeout IMMEDIATE has millis 0`() {
        assert(LockTimeout.IMMEDIATE.millis == 0L)
    }

    @Test
    fun `LockTimeout THIRTY_SECONDS has millis 30000`() {
        assert(LockTimeout.THIRTY_SECONDS.millis == 30_000L)
    }

    @Test
    fun `LockTimeout ONE_MINUTE has millis 60000`() {
        assert(LockTimeout.ONE_MINUTE.millis == 60_000L)
    }

    @Test
    fun `LockTimeout FIVE_MINUTES has millis 300000`() {
        assert(LockTimeout.FIVE_MINUTES.millis == 300_000L)
    }

    // ── Process-death contract ────────────────────────────────────────────────

    @Test
    fun `new LockController instance is always locked when lock is enabled`() = runTest {
        lockEnabledFlow.value = true
        // New controller = new process simulation (no in-memory unlock timestamp)
        val freshController = LockController(context, biometricManager)
        val locked = freshController.isLocked.first()
        assertTrue("New process must start locked when biometric lock is enabled", locked)
    }
}
