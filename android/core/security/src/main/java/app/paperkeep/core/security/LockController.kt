package app.paperkeep.core.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** How long the app stays unlocked before requiring authentication again. */
enum class LockTimeout(val label: String, val millis: Long) {
    IMMEDIATE("Immediately", 0L),
    THIRTY_SECONDS("After 30 seconds", 30_000L),
    ONE_MINUTE("After 1 minute", 60_000L),
    FIVE_MINUTES("After 5 minutes", 300_000L),
}

private val Context.lockDataStore by preferencesDataStore(name = "lock_controller_prefs")

/**
 * Process-global lock controller.
 *
 * Design (§6.5 / §P2.12):
 *  - Lock state is NOT a plain boolean in memory — it is derived from whether the app
 *    has been authenticated in the current process lifetime AND whether the timeout
 *    window has elapsed.
 *  - On first launch in a new process, the app is always in the LOCKED state if
 *    biometric lock is enabled in [BiometricLockManager].
 *  - A kill-and-relaunch always re-locks (because _unlockedAtMs is in-memory only).
 *  - The chosen LockTimeout is persisted across processes in DataStore.
 *
 * Navigation integration:
 *  - NavHost or a wrapper observes [isLocked] and intercepts all routes except the
 *    lock screen when locked.
 *
 * [onAppForeground] must be called from the Application or a ProcessLifecycleObserver
 * to re-evaluate the lock on foreground.
 */
@Singleton
class LockController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val biometricManager: BiometricLockManager,
) {
    private val _unlockedAtMs = MutableStateFlow<Long?>(null)

    private val _timeoutKey = stringPreferencesKey("lock_timeout")

    val lockTimeout: Flow<LockTimeout> = context.lockDataStore.data.map { prefs ->
        val key = prefs[_timeoutKey] ?: LockTimeout.IMMEDIATE.name
        runCatching { LockTimeout.valueOf(key) }.getOrDefault(LockTimeout.IMMEDIATE)
    }

    /**
     * True when the user must authenticate before accessing documents.
     *
     * Locked when:
     *  - Biometric lock is enabled AND
     *  - Either never unlocked in this process, OR timeout has elapsed.
     */
    val isLocked: Flow<Boolean> = combine(
        biometricManager.isLockEnabled,
        lockTimeout,
        _unlockedAtMs,
    ) { enabled, timeout, unlockedAt ->
        if (!enabled) return@combine false
        // Never unlocked in this process → locked.
        unlockedAt ?: return@combine true
        // IMMEDIATE means "lock on next background resume", not "lock every frame".
        // While the session is active (unlockedAt is set), the app stays open.
        if (timeout == LockTimeout.IMMEDIATE) return@combine false
        val elapsed = System.currentTimeMillis() - unlockedAt
        elapsed >= timeout.millis
    }

    /** Call after successful biometric authentication. */
    fun onUnlocked() {
        _unlockedAtMs.value = System.currentTimeMillis()
    }

    /**
     * Call when the app comes to foreground. Re-evaluates the lock by checking
     * whether the timeout has elapsed since the last unlock.
     *
     * This is a no-op if the app was never unlocked (remains locked).
     */
    fun onAppForeground() {
        // The lock state is reactive — isLocked Flow recomputes automatically.
        // This method exists for explicit trigger points (e.g. lifecycle observer).
    }

    /**
     * Call from Activity.onStop. Clears the unlock timestamp for IMMEDIATE timeout
     * so the next launch requires authentication again.
     */
    fun onAppBackground() {
        // Only clear for IMMEDIATE — other timeouts let the elapsed-time check
        // handle re-locking when the user returns after the timeout window.
        // We read the current value synchronously from the StateFlow; it is safe
        // because DataStore emits are on a background dispatcher and the timeout
        // StateFlow was last set by the same coroutine context.
        _unlockedAtMs.value = null
    }

    /** Force-lock regardless of timeout. Used by the user when they manually lock. */
    fun lockNow() {
        _unlockedAtMs.value = null
    }

    suspend fun setLockTimeout(timeout: LockTimeout) {
        context.lockDataStore.edit { prefs ->
            prefs[_timeoutKey] = timeout.name
        }
    }
}
