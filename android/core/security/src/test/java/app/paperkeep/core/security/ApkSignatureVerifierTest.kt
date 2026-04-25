package app.paperkeep.core.security

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ApkSignatureVerifierTest {

    // ── EXPECTED_SHA256 constant ──────────────────────────────────────────────

    @Test
    fun `EXPECTED_SHA256 is exactly 64 hex characters`() {
        assertEquals(64, ApkSignatureVerifier.EXPECTED_SHA256.length)
    }

    @Test
    fun `EXPECTED_SHA256 contains only hex chars`() {
        assertTrue(
            "EXPECTED_SHA256 must be a valid hex string",
            ApkSignatureVerifier.EXPECTED_SHA256.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' },
        )
    }

    // ── isPinConfigured ───────────────────────────────────────────────────────

    @Test
    fun `sentinel all-zeroes value means pin is NOT configured`() {
        // The default value is all zeroes — pin not yet set for production
        assertFalse(
            "Sentinel all-zeroes must mean pin not configured",
            ApkSignatureVerifier.isPinConfigured,
        )
    }

    // ── verify — pin not configured path ─────────────────────────────────────

    @Test
    fun `verify returns Ok when pin is not configured (sentinel)`() {
        // When EXPECTED_SHA256 is all-zeroes (not configured), verify must pass
        val result = ApkSignatureVerifier.verify(ApplicationProvider.getApplicationContext())
        assertEquals(VerificationResult.Ok, result)
    }

    @Test
    fun `isTrusted returns true when pin is not configured`() {
        assertTrue(ApkSignatureVerifier.isTrusted(ApplicationProvider.getApplicationContext()))
    }

    // ── sha256Hex ─────────────────────────────────────────────────────────────

    @Test
    fun `sha256Hex of empty bytes is the standard SHA-256 of empty input`() {
        val result = ApkSignatureVerifier.sha256Hex(ByteArray(0))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            result,
        )
    }

    @Test
    fun `sha256Hex output is always 64 characters`() {
        val inputs = listOf(
            ByteArray(0),
            byteArrayOf(0x00),
            byteArrayOf(0xFF.toByte()),
            "hello world".toByteArray(),
        )
        inputs.forEach { input ->
            assertEquals("sha256 hex must be 64 chars", 64, ApkSignatureVerifier.sha256Hex(input).length)
        }
    }

    @Test
    fun `sha256Hex output is lowercase hex`() {
        val result = ApkSignatureVerifier.sha256Hex("test".toByteArray())
        assertTrue("output must be lowercase hex", result.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `sha256Hex different inputs produce different hashes`() {
        val h1 = ApkSignatureVerifier.sha256Hex("hello".toByteArray())
        val h2 = ApkSignatureVerifier.sha256Hex("world".toByteArray())
        assertFalse("different inputs must produce different hashes", h1 == h2)
    }

    // ── VerificationResult sealed class ──────────────────────────────────────

    @Test
    fun `VerificationResult Ok equals itself`() {
        assertEquals(VerificationResult.Ok, VerificationResult.Ok)
    }

    @Test
    fun `VerificationResult Tampered carries expected and actual`() {
        val result = VerificationResult.Tampered(expected = "aabbcc", actual = "112233")
        assertEquals("aabbcc", result.expected)
        assertEquals("112233", result.actual)
    }

    @Test
    fun `VerificationResult Error carries message`() {
        val result = VerificationResult.Error("no cert")
        assertEquals("no cert", result.message)
    }
}
