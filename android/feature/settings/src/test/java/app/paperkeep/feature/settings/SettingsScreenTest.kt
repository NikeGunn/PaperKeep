package app.paperkeep.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for the settings screen constants and tag values.
 * Compose UI tests belong in androidTest; these cover pure Kotlin logic.
 */
class SettingsScreenTest {

    @Test
    fun `settings test tags are unique`() {
        val tags = listOf(
            TAG_SETTINGS_SCREEN,
            TAG_SETTINGS_SECTION_SECURITY,
            TAG_SETTINGS_SECTION_SCANNING,
            TAG_SETTINGS_SECTION_BACKUP,
            TAG_SETTINGS_SECTION_ABOUT,
        )
        assertEquals("All settings test tags must be unique", tags.size, tags.toSet().size)
    }

    @Test
    fun `settings test tags are non-empty`() {
        listOf(
            TAG_SETTINGS_SCREEN,
            TAG_SETTINGS_SECTION_SECURITY,
            TAG_SETTINGS_SECTION_SCANNING,
            TAG_SETTINGS_SECTION_BACKUP,
            TAG_SETTINGS_SECTION_ABOUT,
        ).forEach { tag ->
            assertFalse("Tag must not be empty: $tag", tag.isEmpty())
        }
    }

    @Test
    fun `no account sync or cloud language in section labels`() {
        val banned = listOf("account", "sync", "cloud", "sign in", "login", "register")
        val labels = listOf(
            TAG_SETTINGS_SECTION_SECURITY,
            TAG_SETTINGS_SECTION_SCANNING,
            TAG_SETTINGS_SECTION_BACKUP,
            TAG_SETTINGS_SECTION_ABOUT,
        )
        labels.forEach { label ->
            banned.forEach { term ->
                assertFalse(
                    "Settings tag '$label' must not contain banned term '$term'",
                    label.contains(term, ignoreCase = true),
                )
            }
        }
    }
}
