package app.paperkeep.core.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * APK signature pin — §6.6 anti-tamper (P3.13).
 *
 * On every launch, compute the SHA-256 of the first signing certificate and
 * compare it to [EXPECTED_SHA256]. A mismatch means the APK was re-signed
 * (stripped ads, pirated clone, injected payload). On mismatch:
 *   - AdMob calls are suppressed (no revenue for the attacker)
 *   - Pro IAP validation is suppressed
 *   - Backup creation is suppressed
 *   - The app still scans documents (we don't brick the app)
 *
 * **How to update EXPECTED_SHA256 for a real release:**
 * 1. Sign the release APK.
 * 2. Run: `keytool -printcert -jarfile app-release.apk`
 *    or:  `apksigner verify --print-certs app-release.apk`
 * 3. Copy the SHA-256 fingerprint (hex, lowercase, no colons).
 * 4. Replace [EXPECTED_SHA256].
 *
 * The all-zeroes sentinel means "pin not configured" — always passes in debug
 * builds and CI (where no release key is present).
 */
object ApkSignatureVerifier {

    /**
     * Expected SHA-256 hex of the release signing certificate.
     *
     * Value "0000...0000" (64 zeroes) = sentinel: pin not configured.
     * Replace with the real fingerprint before publishing to Play.
     */
    const val EXPECTED_SHA256: String =
        "0000000000000000000000000000000000000000000000000000000000000000"

    /** True if the pin is the sentinel (not yet configured). */
    val isPinConfigured: Boolean get() = EXPECTED_SHA256 != "0".repeat(64)

    /**
     * Verify the current APK's signature against [EXPECTED_SHA256].
     *
     * @return [VerificationResult.Ok] if the signature matches or the pin is
     *         not configured. [VerificationResult.Tampered] on mismatch.
     *         [VerificationResult.Error] if the signature cannot be read.
     */
    fun verify(context: Context): VerificationResult {
        if (!isPinConfigured) return VerificationResult.Ok

        return try {
            val cert = getSigningCert(context) ?: return VerificationResult.Error("no signing cert")
            val actual = sha256Hex(cert.toByteArray())
            if (actual.equals(EXPECTED_SHA256, ignoreCase = true)) {
                VerificationResult.Ok
            } else {
                VerificationResult.Tampered(expected = EXPECTED_SHA256, actual = actual)
            }
        } catch (e: Exception) {
            VerificationResult.Error(e.message ?: "unknown")
        }
    }

    /**
     * True if the APK has NOT been tampered with (or pin is not configured).
     * Convenience wrapper for callers that only need a boolean.
     */
    fun isTrusted(context: Context): Boolean = verify(context) == VerificationResult.Ok

    // ── Helpers ───────────────────────────────────────────────────────────────

    internal fun getSigningCert(context: Context): Signature? {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info: PackageInfo = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            val info: PackageInfo = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES,
            )
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()
        }
    }

    internal fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

// ── Result type ───────────────────────────────────────────────────────────────

sealed interface VerificationResult {
    /** Signature matches pin (or pin not configured). */
    data object Ok : VerificationResult

    /** Signature does NOT match — APK has been re-signed. */
    data class Tampered(val expected: String, val actual: String) : VerificationResult

    /** Could not read the signing certificate. */
    data class Error(val message: String) : VerificationResult
}
