package app.paperkeep.core.security

import android.os.Build
import java.io.File
import java.net.Socket

/**
 * Best-effort root / Magisk / Frida / emulator detection (P3.14 — §6.6).
 *
 * **Policy:** never blocks the app. On a positive signal:
 *  - Pro IAP unlock is disabled (can't reliably verify purchase integrity)
 *  - Backup creation is disabled (key export risk on rooted device)
 *  - A one-time info card is shown in Settings (handled by [IntegrityGate])
 *
 * All probes are heuristic and bypass-able by a determined attacker — that's
 * acceptable. We are not an MDM solution; we are a document scanner that
 * wants to protect honest users from accidental key exposure.
 *
 * Injectable for unit testing via the [RootProbe] / [FridaProbe] interfaces.
 */
object DeviceIntegrityChecker {

    fun check(
        rootProbe:    RootProbe    = DefaultRootProbe,
        fridaProbe:   FridaProbe   = DefaultFridaProbe,
        emulatorProbe: EmulatorProbe = DefaultEmulatorProbe,
    ): RootCheckResult {
        val suFound      = rootProbe.hasSu()
        val magiskFound  = rootProbe.hasMagisk()
        val buildTagsTest = rootProbe.buildTagsContainTest()

        val fridaLoaded  = fridaProbe.isFridaGadgetLoaded()
        val fridaPort    = fridaProbe.isFridaPortOpen()

        val isEmulator   = emulatorProbe.isEmulator()

        return RootCheckResult(
            hasSu             = suFound,
            hasMagisk         = magiskFound,
            buildTagsContainTest = buildTagsTest,
            isFridaGadgetLoaded  = fridaLoaded,
            isFridaPortOpen      = fridaPort,
            isEmulatorFingerprint = isEmulator,
        )
    }
}

data class RootCheckResult(
    val hasSu:                   Boolean = false,
    val hasMagisk:               Boolean = false,
    val buildTagsContainTest:    Boolean = false,
    val isFridaGadgetLoaded:     Boolean = false,
    val isFridaPortOpen:         Boolean = false,
    val isEmulatorFingerprint:   Boolean = false,
) {
    val isRooted:        Boolean get() = hasSu || hasMagisk || buildTagsContainTest
    val isFridaDetected: Boolean get() = isFridaGadgetLoaded || isFridaPortOpen
    val isEmulator:      Boolean get() = isEmulatorFingerprint
}

// ── Probe interfaces ─────────────────────────────────────────────────────────

interface RootProbe {
    fun hasSu(): Boolean
    fun hasMagisk(): Boolean
    fun buildTagsContainTest(): Boolean
}

interface FridaProbe {
    fun isFridaGadgetLoaded(): Boolean
    fun isFridaPortOpen(): Boolean
}

interface EmulatorProbe {
    fun isEmulator(): Boolean
}

// ── Default (production) probes ───────────────────────────────────────────────

object DefaultRootProbe : RootProbe {

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/su",
        "/system/bin/.ext/.su",
        "/system/usr/we-need-root/su-backup",
        "/su/bin/su",
    )

    private val MAGISK_INDICATORS = listOf(
        "/sbin/.magisk",
        "/sbin/.core/mirror",
        "/sbin/.core/img",
        "/data/adb/magisk",
    )

    override fun hasSu(): Boolean = SU_PATHS.any { File(it).exists() }

    override fun hasMagisk(): Boolean = MAGISK_INDICATORS.any { File(it).exists() }

    override fun buildTagsContainTest(): Boolean =
        Build.TAGS?.contains("test-keys") == true
}

object DefaultFridaProbe : FridaProbe {

    private val FRIDA_LIBRARIES = listOf(
        "frida-gadget", "libfrida", "libsubstrate", "libcydiasubstrate",
    )

    override fun isFridaGadgetLoaded(): Boolean {
        return try {
            val mapsFile = File("/proc/self/maps")
            if (!mapsFile.exists()) return false
            mapsFile.bufferedReader().use { reader ->
                reader.lineSequence().any { line ->
                    FRIDA_LIBRARIES.any { lib -> line.contains(lib, ignoreCase = true) }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun isFridaPortOpen(): Boolean {
        // Frida's default agent port is 27042
        return try {
            Socket("127.0.0.1", 27042).use { true }
        } catch (_: Exception) {
            false
        }
    }
}

object DefaultEmulatorProbe : EmulatorProbe {

    private val EMULATOR_FINGERPRINTS = listOf(
        "generic", "unknown", "google_sdk", "emulator", "Android SDK built for x86",
    )

    private val EMULATOR_HARDWARE = listOf(
        "goldfish", "ranchu", "vbox86",
    )

    override fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT?.lowercase() ?: ""
        val hardware    = Build.HARDWARE?.lowercase() ?: ""
        val model       = Build.MODEL?.lowercase() ?: ""
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""

        return EMULATOR_FINGERPRINTS.any { fingerprint.contains(it) }
            || EMULATOR_HARDWARE.any { hardware.contains(it) }
            || model.contains("sdk")
            || model.contains("emulator")
            || manufacturer.contains("genymotion")
            || (Build.BRAND?.startsWith("generic") == true && Build.DEVICE?.startsWith("generic") == true)
    }
}
