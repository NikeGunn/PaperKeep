package app.paperkeep.core.network.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies the APK signing certificate on launch.
 *
 * Computes SHA-256 of the installed signing certificate and compares it to a
 * compile-time constant.  If they differ the APK was re-signed (tampered).
 *
 * Behaviour:
 * - Debug builds: always returns [TamperResult.OK] (certificate changes with each debug keystore)
 * - Release builds: compares against [RELEASE_CERT_SHA256]
 *
 * IMPORTANT: update [RELEASE_CERT_SHA256] after the first release-signed build:
 *   keytool -printcert -jarfile app-release.aab | grep SHA256
 * Then replace the placeholder below with the colon-separated hex digest.
 */
@Singleton
class TamperChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * SHA-256 fingerprint of the production signing certificate.
     * Format: colon-separated hex bytes, e.g. "AB:CD:EF:..."
     *
     * Set to empty string until the first release keystore is generated.
     * When empty the check is skipped (treated as OK) so dev builds always pass.
     */
    private val RELEASE_CERT_SHA256 = ""

    sealed class TamperResult {
        object OK : TamperResult()

        /** APK signing certificate does not match the expected fingerprint. */
        data class Tampered(val actualFingerprint: String) : TamperResult()

        /** Check could not run (e.g. PackageManager unavailable). */
        data class Error(val message: String) : TamperResult()
    }

    /**
     * Performs the tamper check.
     *
     * - Debug builds return [TamperResult.OK] without checking.
     * - Release builds return [TamperResult.OK] only if certificate fingerprint matches.
     */
    fun check(): TamperResult {
        if (isDebugBuild()) return TamperResult.OK
        if (RELEASE_CERT_SHA256.isEmpty()) return TamperResult.OK

        return try {
            val fingerprint = getSigningCertSha256()
            if (fingerprint.equals(RELEASE_CERT_SHA256, ignoreCase = true)) {
                TamperResult.OK
            } else {
                TamperResult.Tampered(fingerprint)
            }
        } catch (e: Exception) {
            TamperResult.Error(e.message ?: "Unknown error")
        }
    }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun getSigningCertSha256(): String {
        val packageManager = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            ).signingInfo ?: throw IllegalStateException("signingInfo is null")
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES,
            ).signatures ?: throw IllegalStateException("signatures array is null")
        }

        val cert = signatures.firstOrNull()
            ?: throw IllegalStateException("No signing certificate found")

        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.toByteArray())
        return hash.joinToString(":") { "%02X".format(it) }
    }

    private fun isDebugBuild(): Boolean =
        context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
}
