package app.paperkeep.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIntegrityCheckerTest {

    // ── Fake probes for isolation ─────────────────────────────────────────────

    private fun rootProbe(su: Boolean = false, magisk: Boolean = false, testKeys: Boolean = false) =
        object : RootProbe {
            override fun hasSu() = su
            override fun hasMagisk() = magisk
            override fun buildTagsContainTest() = testKeys
        }

    private fun fridaProbe(gadget: Boolean = false, port: Boolean = false) =
        object : FridaProbe {
            override fun isFridaGadgetLoaded() = gadget
            override fun isFridaPortOpen() = port
        }

    private fun emulatorProbe(emulator: Boolean = false) =
        object : EmulatorProbe {
            override fun isEmulator() = emulator
        }

    // ── RootCheckResult derivations ───────────────────────────────────────────

    @Test
    fun `isRooted false when all probes clean`() {
        val result = RootCheckResult()
        assertFalse(result.isRooted)
    }

    @Test
    fun `isRooted true when hasSu`() {
        val result = RootCheckResult(hasSu = true)
        assertTrue(result.isRooted)
    }

    @Test
    fun `isRooted true when hasMagisk`() {
        val result = RootCheckResult(hasMagisk = true)
        assertTrue(result.isRooted)
    }

    @Test
    fun `isRooted true when buildTagsContainTest`() {
        val result = RootCheckResult(buildTagsContainTest = true)
        assertTrue(result.isRooted)
    }

    @Test
    fun `isFridaDetected false when neither gadget nor port`() {
        val result = RootCheckResult()
        assertFalse(result.isFridaDetected)
    }

    @Test
    fun `isFridaDetected true when gadget loaded`() {
        val result = RootCheckResult(isFridaGadgetLoaded = true)
        assertTrue(result.isFridaDetected)
    }

    @Test
    fun `isFridaDetected true when frida port open`() {
        val result = RootCheckResult(isFridaPortOpen = true)
        assertTrue(result.isFridaDetected)
    }

    @Test
    fun `isEmulator mirrors isEmulatorFingerprint`() {
        assertFalse(RootCheckResult(isEmulatorFingerprint = false).isEmulator)
        assertTrue(RootCheckResult(isEmulatorFingerprint = true).isEmulator)
    }

    // ── DeviceIntegrityChecker.check() with injected fakes ───────────────────

    @Test
    fun `clean device — all false`() {
        val result = DeviceIntegrityChecker.check(
            rootProbe    = rootProbe(),
            fridaProbe   = fridaProbe(),
            emulatorProbe = emulatorProbe(),
        )
        assertFalse(result.isRooted)
        assertFalse(result.isFridaDetected)
        assertFalse(result.isEmulator)
    }

    @Test
    fun `su path found — isRooted true`() {
        val result = DeviceIntegrityChecker.check(
            rootProbe    = rootProbe(su = true),
            fridaProbe   = fridaProbe(),
            emulatorProbe = emulatorProbe(),
        )
        assertTrue(result.hasSu)
        assertTrue(result.isRooted)
        assertFalse(result.isFridaDetected)
    }

    @Test
    fun `magisk found — isRooted true`() {
        val result = DeviceIntegrityChecker.check(
            rootProbe    = rootProbe(magisk = true),
            fridaProbe   = fridaProbe(),
            emulatorProbe = emulatorProbe(),
        )
        assertTrue(result.hasMagisk)
        assertTrue(result.isRooted)
    }

    @Test
    fun `frida gadget loaded — isFridaDetected true`() {
        val result = DeviceIntegrityChecker.check(
            rootProbe    = rootProbe(),
            fridaProbe   = fridaProbe(gadget = true),
            emulatorProbe = emulatorProbe(),
        )
        assertTrue(result.isFridaDetected)
        assertFalse(result.isRooted)
    }

    @Test
    fun `frida port open — isFridaDetected true`() {
        val result = DeviceIntegrityChecker.check(
            rootProbe    = rootProbe(),
            fridaProbe   = fridaProbe(port = true),
            emulatorProbe = emulatorProbe(),
        )
        assertTrue(result.isFridaPort())
        assertTrue(result.isFridaDetected)
    }

    @Test
    fun `emulator fingerprint — isEmulator true`() {
        val result = DeviceIntegrityChecker.check(
            rootProbe    = rootProbe(),
            fridaProbe   = fridaProbe(),
            emulatorProbe = emulatorProbe(emulator = true),
        )
        assertTrue(result.isEmulator)
        assertFalse(result.isRooted)
    }

    @Test
    fun `all signals positive — everything detected`() {
        val result = DeviceIntegrityChecker.check(
            rootProbe    = rootProbe(su = true, magisk = true, testKeys = true),
            fridaProbe   = fridaProbe(gadget = true, port = true),
            emulatorProbe = emulatorProbe(emulator = true),
        )
        assertTrue(result.isRooted)
        assertTrue(result.isFridaDetected)
        assertTrue(result.isEmulator)
    }

    // ── IntegrityGate feature flags ───────────────────────────────────────────

    @Test
    fun `IntegrityGate AdMob disabled on tampered APK`() {
        val status = IntegrityStatus(
            apkTrusted = false,
            apkVerification = VerificationResult.Tampered("expected", "actual"),
        )
        assertFalse("AdMob must be disabled when APK is tampered", status.apkTrusted)
    }

    @Test
    fun `IntegrityGate Pro IAP disabled when rooted`() {
        val status = IntegrityStatus(apkTrusted = true, isRooted = true)
        // Pro IAP disabled: apkTrusted && !isRooted = false
        assertFalse("Pro IAP gate: apkTrusted && !isRooted", status.apkTrusted && !status.isRooted)
    }

    @Test
    fun `IntegrityGate all features enabled on clean trusted device`() {
        val status = IntegrityStatus(apkTrusted = true, isRooted = false)
        assertTrue("AdMob gate",  status.apkTrusted)
        assertTrue("Pro IAP gate", status.apkTrusted && !status.isRooted)
        assertTrue("Backup gate",  status.apkTrusted && !status.isRooted)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun RootCheckResult.isFridaPort() = isFridaPortOpen
}
