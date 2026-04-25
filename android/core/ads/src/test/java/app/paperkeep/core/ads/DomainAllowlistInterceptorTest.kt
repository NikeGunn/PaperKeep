package app.paperkeep.core.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainAllowlistInterceptorTest {

    // ── Allowed domains ───────────────────────────────────────────────────────

    @Test fun `doubleclick net root is allowed`()       = allow("doubleclick.net")
    @Test fun `subdomain of doubleclick allowed`()      = allow("ad.doubleclick.net")
    @Test fun `deep subdomain of doubleclick allowed`() = allow("a.b.doubleclick.net")

    @Test fun `google com root is allowed`()     = allow("google.com")
    @Test fun `subdomain of google allowed`()    = allow("www.google.com")
    @Test fun `admob google subdomain allowed`() = allow("admob.googleapis.com")

    @Test fun `googleapis com root allowed`()  = allow("googleapis.com")
    @Test fun `subdomain googleapis allowed`() = allow("pubads.g.doubleclick.net")

    @Test fun `googleusercontent com allowed`()          = allow("googleusercontent.com")
    @Test fun `subdomain googleusercontent allowed`()    = allow("lh3.googleusercontent.com")

    @Test fun `gstatic com root allowed`()    = allow("gstatic.com")
    @Test fun `subdomain gstatic allowed`()   = allow("fonts.gstatic.com")

    // ── Blocked domains ───────────────────────────────────────────────────────

    @Test fun `facebook com is blocked`()     = block("facebook.com")
    @Test fun `crashlytics blocked`()         = block("reports.crashlytics.com")
    @Test fun `sentry io blocked`()           = block("sentry.io")
    @Test fun `mixpanel com blocked`()        = block("api.mixpanel.com")
    @Test fun `amazonaws blocked`()           = block("s3.amazonaws.com")
    @Test fun `example com blocked`()         = block("example.com")
    @Test fun `empty string blocked`()        = block("")
    @Test fun `lookalike google blocked`()    = block("evilgoogle.com")
    @Test fun `lookalike gstatic blocked`()   = block("evgstatic.com")

    // ── checkOrThrow ──────────────────────────────────────────────────────────

    @Test
    fun `checkOrThrow does not throw for allowed host`() {
        DomainAllowlistInterceptor.checkOrThrow("googleapis.com") // must not throw
    }

    @Test(expected = BlockedHostException::class)
    fun `checkOrThrow throws BlockedHostException for disallowed host`() {
        DomainAllowlistInterceptor.checkOrThrow("evil.com")
    }

    // ── Case-insensitivity ────────────────────────────────────────────────────

    @Test fun `domain check is case-insensitive`() = allow("GOOGLE.COM")
    @Test fun `mixed case subdomain allowed`()     = allow("WWW.Googleapis.COM")

    // ── ALLOWED_DOMAINS set ───────────────────────────────────────────────────

    @Test
    fun `ALLOWED_DOMAINS set has exactly 5 entries`() {
        org.junit.Assert.assertEquals(5, DomainAllowlistInterceptor.ALLOWED_DOMAINS.size)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun allow(host: String) =
        assertTrue("$host should be allowed", DomainAllowlistInterceptor.isAllowed(host))

    private fun block(host: String) =
        assertFalse("$host should be blocked", DomainAllowlistInterceptor.isAllowed(host))
}
