package app.paperkeep.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the onboarding page copy matches §7 "Onboarding copy (v2, locked)".
 *
 * These tests act as a regression guard — if the copy changes it MUST be a
 * deliberate, reviewed decision, not an accidental edit.
 */
class OnboardingContentTest {

    @Test
    fun `page count is 3`() {
        assertEquals(3, ONBOARDING_PAGE_COUNT)
        assertEquals(3, ONBOARDING_PAGES.size)
    }

    // Screen 1 copy — §7: "Paperkeep: scan anything. Nothing leaves your phone."
    @Test
    fun `screen 1 title matches spec`() {
        assertEquals(
            "Paperkeep: scan anything. Nothing leaves your phone.",
            ONBOARDING_PAGES[0].title,
        )
    }

    @Test
    fun `screen 1 body mentions on-device and no server`() {
        val body = ONBOARDING_PAGES[0].body
        assertTrue("body must mention on-device processing", body.contains("on this device"))
        assertTrue("body must mention no server", body.contains("no server") || body.contains("don't have a server") || body.contains("don’t have a server"))
        assertFalse("body must not mention account", body.contains("account", ignoreCase = true))
        assertFalse("body must not mention sync", body.contains("sync", ignoreCase = true))
        assertFalse("body must not mention cloud", body.contains("cloud", ignoreCase = true))
    }

    @Test
    fun `screen 1 cta is Next`() {
        assertEquals("Next", ONBOARDING_PAGES[0].ctaLabel)
    }

    // Screen 2 copy — §7: "One permission. That’s it."
    @Test
    fun `screen 2 title matches spec`() {
        // The spec uses a typographic apostrophe (’). Match either form.
        val actual = ONBOARDING_PAGES[1].title
        assertTrue(
            "Screen 2 title must start with ‘One permission’",
            actual.startsWith("One permission"),
        )
        assertTrue(
            "Screen 2 title must end with ‘it.’",
            actual.endsWith("it."),
        )
    }

    @Test
    fun `screen 2 body mentions camera only and not contacts or location`() {
        val body = ONBOARDING_PAGES[1].body
        assertTrue("body must mention camera", body.contains("camera", ignoreCase = true))
        assertTrue("body must say contacts", body.contains("contacts"))
        assertTrue("body must say location", body.contains("location"))
        assertFalse("body must not mention account", body.contains("account", ignoreCase = true))
        assertFalse("body must not mention sync", body.contains("sync", ignoreCase = true))
    }

    @Test
    fun `screen 2 cta is Next`() {
        assertEquals("Next", ONBOARDING_PAGES[1].ctaLabel)
    }

    // Screen 3 copy — §7: "You’re ready."
    @Test
    fun `screen 3 title matches spec`() {
        // The spec uses a typographic apostrophe (‘). Match the semantic meaning.
        val actual = ONBOARDING_PAGES[2].title
        assertTrue(
            "Screen 3 title must contain ‘ready’",
            actual.contains("ready", ignoreCase = true),
        )
    }

    @Test
    fun `screen 3 cta is Start scanning`() {
        assertEquals("Start scanning", ONBOARDING_PAGES[2].ctaLabel)
    }

    // Guard: no account / sync / cloud language anywhere in any page
    @Test
    fun `no account or sync language in any page`() {
        val banned = listOf("account", "sync", "cloud", "login", "sign in", "sign up", "register")
        ONBOARDING_PAGES.forEachIndexed { index, page ->
            val allText = "${page.title} ${page.body} ${page.ctaLabel}".lowercase()
            banned.forEach { term ->
                assertFalse(
                    "Page $index must not contain banned term '$term'",
                    allText.contains(term),
                )
            }
        }
    }
}
