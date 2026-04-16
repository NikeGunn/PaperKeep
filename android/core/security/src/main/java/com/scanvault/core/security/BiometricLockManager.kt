package com.scanvault.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.securityDataStore by preferencesDataStore(name = "security_prefs")

/**
 * Manages biometric app-lock functionality.
 *
 * Responsibilities:
 *  - Persists the "lock enabled" preference in DataStore.
 *  - Checks device biometric hardware availability.
 *  - Presents the [BiometricPrompt] when authentication is required.
 *  - Provides a graceful fallback to device PIN/password when biometric hardware
 *    is unavailable (via [DEVICE_CREDENTIAL] authenticator).
 */
@Singleton
class BiometricLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")

        /** Authenticators accepted: strong biometric **or** device credential (PIN/password). */
        private const val ACCEPTED_AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }

    // ── DataStore ─────────────────────────────────────────────────────────────

    /**
     * Observable stream of whether the biometric lock is enabled.
     * Default is `false` (disabled until the user opts in).
     */
    val isLockEnabled: Flow<Boolean> =
        context.securityDataStore.data.map { prefs ->
            prefs[KEY_LOCK_ENABLED] ?: false
        }

    /** Persist the user's lock preference. */
    suspend fun setLockEnabled(enabled: Boolean) {
        context.securityDataStore.edit { prefs ->
            prefs[KEY_LOCK_ENABLED] = enabled
        }
    }

    // ── Hardware check ────────────────────────────────────────────────────────

    /**
     * Returns `true` when the device can authenticate with at least one of:
     *  - Strong biometric (fingerprint, face, iris)
     *  - Device credential (PIN, pattern, password — fallback)
     *
     * Returns `false` when no secure lock screen is configured at all.
     */
    fun isBiometricAvailable(): Boolean {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(ACCEPTED_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            -> true
            else -> false
        }
    }

    /**
     * Returns `true` only when a **strong** biometric (fingerprint/face/iris) is
     * enrolled — i.e., hardware is present AND the user has enrolled credentials.
     */
    fun isStrongBiometricEnrolled(): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // ── BiometricPrompt ───────────────────────────────────────────────────────

    /**
     * Show the system biometric prompt from [activity].
     *
     * Calls [onSuccess] when the user authenticates successfully, or
     * [onFailure] with a descriptive message on error/cancellation.
     *
     * Graceful degradation: if no biometric is enrolled, the system automatically
     * falls back to PIN/password (via [DEVICE_CREDENTIAL]).
     * If there is no secure lock screen at all, [onFailure] is called immediately.
     */
    fun showPrompt(
        activity: FragmentActivity,
        title: String = "Unlock ScanVault",
        subtitle: String = "Authenticate to access your documents",
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (!isBiometricAvailable()) {
            onFailure("No secure lock screen configured. Please set up a PIN or biometric in Settings.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure(errString.toString())
            }

            override fun onAuthenticationFailed() {
                // Single finger/face attempt failed — the system handles retries automatically.
                // We intentionally do not call onFailure here; the system shows a retry UI.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ACCEPTED_AUTHENTICATORS)
            .build()

        prompt.authenticate(promptInfo)
    }
}
