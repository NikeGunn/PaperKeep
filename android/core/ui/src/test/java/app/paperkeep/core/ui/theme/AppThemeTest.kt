package app.paperkeep.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [AppTheme] enum — key mapping and label correctness.
 */
class AppThemeTest {

    @Test
    fun `fromKey returns SYSTEM for unknown key`() {
        assertEquals(AppTheme.SYSTEM, AppTheme.fromKey("unknown"))
    }

    @Test
    fun `fromKey returns SYSTEM for empty string`() {
        assertEquals(AppTheme.SYSTEM, AppTheme.fromKey(""))
    }

    @Test
    fun `fromKey returns SYSTEM for system key`() {
        assertEquals(AppTheme.SYSTEM, AppTheme.fromKey("system"))
    }

    @Test
    fun `fromKey returns LIGHT for light key`() {
        assertEquals(AppTheme.LIGHT, AppTheme.fromKey("light"))
    }

    @Test
    fun `fromKey returns DARK for dark key`() {
        assertEquals(AppTheme.DARK, AppTheme.fromKey("dark"))
    }

    @Test
    fun `all entries have unique keys`() {
        val keys = AppTheme.entries.map { it.key }
        assertEquals(keys.distinct().size, keys.size)
    }

    @Test
    fun `all entries have non-empty labels`() {
        AppTheme.entries.forEach { theme ->
            assert(theme.label.isNotBlank()) { "${theme.name} has blank label" }
        }
    }

    @Test
    fun `system theme label is follow system`() {
        assertEquals("Follow system", AppTheme.SYSTEM.label)
    }

    @Test
    fun `light theme label is Light`() {
        assertEquals("Light", AppTheme.LIGHT.label)
    }

    @Test
    fun `dark theme label is Dark`() {
        assertEquals("Dark", AppTheme.DARK.label)
    }

    @Test
    fun `all keys can be round-tripped through fromKey`() {
        AppTheme.entries.forEach { theme ->
            assertEquals(theme, AppTheme.fromKey(theme.key))
        }
    }
}
