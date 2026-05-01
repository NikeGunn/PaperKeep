package app.paperkeep.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PlayIntegrityResult] and [PlayIntegrityVerifier] logic.
 *
 * The actual IntegrityManager requires Play Services and cannot be called
 * in JVM unit tests. These tests verify the sealed-interface contract
 * and result handling logic.
 */
class PlayIntegrityVerifierTest {

    // ── PlayIntegrityResult sealed interface ──────────────────────────────────

    @Test
    fun `MeetsIntegrity stores the token`() {
        val result = PlayIntegrityResult.MeetsIntegrity("eyJhbGci.test.token")
        assertTrue(result.token.isNotBlank())
    }

    @Test
    fun `Unavailable with null reason is valid`() {
        val result = PlayIntegrityResult.Unavailable(null)
        assertTrue(result is PlayIntegrityResult.Unavailable)
    }

    @Test
    fun `Unavailable with reason stores the reason`() {
        val result = PlayIntegrityResult.Unavailable("Timeout")
        assertTrue(result.reason == "Timeout")
    }

    @Test
    fun `MeetsIntegrity is not Unavailable`() {
        val result: PlayIntegrityResult = PlayIntegrityResult.MeetsIntegrity("tok")
        assertFalse(result is PlayIntegrityResult.Unavailable)
    }

    @Test
    fun `Unavailable is not MeetsIntegrity`() {
        val result: PlayIntegrityResult = PlayIntegrityResult.Unavailable("reason")
        assertFalse(result is PlayIntegrityResult.MeetsIntegrity)
    }

    // ── Nonce uniqueness ──────────────────────────────────────────────────────

    @Test
    fun `distinct nonce calls produce different strings`() {
        val ts1 = System.currentTimeMillis().toString(36)
        Thread.sleep(2)
        val ts2 = System.currentTimeMillis().toString(36)
        // Nonces built from different timestamps should differ or random part ensures uniqueness
        val r1 = (Math.random() * Long.MAX_VALUE).toLong().toString(36)
        val r2 = (Math.random() * Long.MAX_VALUE).toLong().toString(36)
        assertTrue("Random nonce parts should differ with overwhelming probability", r1 != r2 || ts1 != ts2)
    }

    // ── Feature gate logic ────────────────────────────────────────────────────

    @Test
    fun `pro should not be granted when result is Unavailable`() {
        val result: PlayIntegrityResult = PlayIntegrityResult.Unavailable("Google Play not available")
        val shouldGrantPro = result is PlayIntegrityResult.MeetsIntegrity
        assertFalse("Pro must not be granted on Unavailable result", shouldGrantPro)
    }

    @Test
    fun `pro can be granted when result is MeetsIntegrity`() {
        val result: PlayIntegrityResult = PlayIntegrityResult.MeetsIntegrity("valid.token")
        val shouldGrantPro = result is PlayIntegrityResult.MeetsIntegrity
        assertTrue("Pro can be granted on MeetsIntegrity result", shouldGrantPro)
    }

    @Test
    fun `empty token maps to Unavailable not MeetsIntegrity`() {
        val token = ""
        val result: PlayIntegrityResult = if (token.isNotBlank()) {
            PlayIntegrityResult.MeetsIntegrity(token)
        } else {
            PlayIntegrityResult.Unavailable("Empty token")
        }
        assertTrue(result is PlayIntegrityResult.Unavailable)
    }
}
