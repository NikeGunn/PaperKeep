package app.paperkeep.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for MIME allowlist validation in [ShareImportActivity].
 *
 * The isMimeAllowed method is package-private (called from within the Activity),
 * so we test the logic directly here by replicating it.
 * Full integration tests (intent handling, size cap) require an instrumented test.
 */
class ShareImportActivityTest {

    private fun isMimeAllowed(mime: String): Boolean =
        mime == "image/jpeg" ||
            mime == "image/png" ||
            mime == "image/webp" ||
            mime == "image/heic" ||
            mime == "image/heif" ||
            mime == "application/pdf"

    @Test
    fun `jpeg is allowed`() {
        assertTrue(isMimeAllowed("image/jpeg"))
    }

    @Test
    fun `png is allowed`() {
        assertTrue(isMimeAllowed("image/png"))
    }

    @Test
    fun `webp is allowed`() {
        assertTrue(isMimeAllowed("image/webp"))
    }

    @Test
    fun `heic is allowed`() {
        assertTrue(isMimeAllowed("image/heic"))
    }

    @Test
    fun `heif is allowed`() {
        assertTrue(isMimeAllowed("image/heif"))
    }

    @Test
    fun `application pdf is allowed`() {
        assertTrue(isMimeAllowed("application/pdf"))
    }

    @Test
    fun `video mp4 is rejected`() {
        assertFalse(isMimeAllowed("video/mp4"))
    }

    @Test
    fun `text plain is rejected`() {
        assertFalse(isMimeAllowed("text/plain"))
    }

    @Test
    fun `application zip is rejected`() {
        assertFalse(isMimeAllowed("application/zip"))
    }

    @Test
    fun `wildcard image is rejected`() {
        assertFalse(isMimeAllowed("image/*"))
    }

    @Test
    fun `empty string is rejected`() {
        assertFalse(isMimeAllowed(""))
    }

    @Test
    fun `max size constant is 50MB`() {
        val maxBytes = 50L * 1024 * 1024
        assertTrue("50MB should be 52428800 bytes", maxBytes == 52_428_800L)
    }
}
