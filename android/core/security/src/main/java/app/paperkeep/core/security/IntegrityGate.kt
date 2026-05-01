package app.paperkeep.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for feature-gate decisions based on APK integrity
 * and device security posture (P3.13 + P3.14).
 *
 * On app start, call [evaluate]. All feature-flag decisions are then driven by
 * the resulting [IntegrityStatus]. The app never hard-blocks on any flag —
 * scanning always works.
 *
 * Feature gates controlled by this class:
 *   - [isAdMobEnabled]     — false if APK is re-signed
 *   - [isProIapEnabled]    — false if APK re-signed OR device is rooted
 *   - [isBackupEnabled]    — false if APK re-signed OR device is rooted
 */
@Singleton
class IntegrityGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signatureVerifier: ApkSignatureVerifier,
    private val deviceChecker: DeviceIntegrityChecker,
) {

    private val _status = MutableStateFlow(IntegrityStatus())
    val status: StateFlow<IntegrityStatus> = _status.asStateFlow()

    /** The current evaluated status (synchronous convenience accessor). */
    val currentStatus: IntegrityStatus get() = _status.value

    val isAdMobEnabled:  Boolean get() = currentStatus.apkTrusted
    val isProIapEnabled: Boolean get() = currentStatus.apkTrusted && !currentStatus.isRooted
    val isBackupEnabled: Boolean get() = currentStatus.apkTrusted && !currentStatus.isRooted

    /**
     * Run all integrity checks and update [status].
     * Call once at app startup (e.g. from [PaperkeepApplication.onCreate]).
     */
    fun evaluate() {
        val apkResult = signatureVerifier.verify(context)
        val apkTrusted = apkResult is VerificationResult.Ok

        val rootResult = deviceChecker.check()

        _status.value = IntegrityStatus(
            apkTrusted          = apkTrusted,
            apkVerification     = apkResult,
            isRooted            = rootResult.isRooted,
            isFridaDetected     = rootResult.isFridaDetected,
            isEmulator          = rootResult.isEmulator,
            rootCheckDetails    = rootResult,
        )
    }
}

data class IntegrityStatus(
    val apkTrusted:       Boolean            = true,
    val apkVerification:  VerificationResult = VerificationResult.Ok,
    val isRooted:         Boolean            = false,
    val isFridaDetected:  Boolean            = false,
    val isEmulator:       Boolean            = false,
    val rootCheckDetails: RootCheckResult    = RootCheckResult(),
)
