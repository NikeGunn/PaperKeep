package app.paperkeep.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Material 3 theme — color correctness, dynamic-color fallback, shape wiring.
 *
 * Dynamic color (Android 12+) reads from wallpaper and is unavailable in Robolectric,
 * so we test the static saffron palette (dynamicColor = false) for determinism.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── Light palette ─────────────────────────────────────────────────────────

    @Test
    fun lightTheme_primary_matchesSaffronDarkTone() {
        var primary = Color.Unspecified
        composeRule.setContent {
            PaperkeepTheme(darkTheme = false, dynamicColor = false) {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        composeRule.waitForIdle()
        assertEquals("Light primary must be saffron dark tone", LightColors.primary, primary)
    }

    @Test
    fun lightTheme_background_isWarmNearWhite() {
        var background = Color.Unspecified
        composeRule.setContent {
            PaperkeepTheme(darkTheme = false, dynamicColor = false) {
                background = MaterialTheme.colorScheme.background
            }
        }
        composeRule.waitForIdle()
        assertEquals(LightColors.background, background)
    }

    @Test
    fun lightTheme_error_isRed() {
        var error = Color.Unspecified
        composeRule.setContent {
            PaperkeepTheme(darkTheme = false, dynamicColor = false) {
                error = MaterialTheme.colorScheme.error
            }
        }
        composeRule.waitForIdle()
        assertEquals(LightColors.error, error)
    }

    @Test
    fun lightTheme_onPrimary_isWhite() {
        var onPrimary = Color.Unspecified
        composeRule.setContent {
            PaperkeepTheme(darkTheme = false, dynamicColor = false) {
                onPrimary = MaterialTheme.colorScheme.onPrimary
            }
        }
        composeRule.waitForIdle()
        assertEquals(LightColors.onPrimary, onPrimary)
    }

    @Test
    fun lightTheme_tertiary_isPresent() {
        var tertiary = Color.Unspecified
        composeRule.setContent {
            PaperkeepTheme(darkTheme = false, dynamicColor = false) {
                tertiary = MaterialTheme.colorScheme.tertiary
            }
        }
        composeRule.waitForIdle()
        assertEquals(LightColors.tertiary, tertiary)
    }

    // ── Dark palette ──────────────────────────────────────────────────────────

    @Test
    fun darkTheme_primary_isSaffronLightTone() {
        var primary = Color.Unspecified
        composeRule.setContent {
            PaperkeepTheme(darkTheme = true, dynamicColor = false) {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        composeRule.waitForIdle()
        assertEquals("Dark primary must be saffron light tone", DarkColors.primary, primary)
    }

    @Test
    fun darkTheme_background_isDarkWarmSurface() {
        var background = Color.Unspecified
        composeRule.setContent {
            PaperkeepTheme(darkTheme = true, dynamicColor = false) {
                background = MaterialTheme.colorScheme.background
            }
        }
        composeRule.waitForIdle()
        assertEquals(DarkColors.background, background)
    }

    @Test
    fun darkTheme_tertiary_isPresent() {
        var tertiary = Color.Unspecified
        composeRule.setContent {
            PaperkeepTheme(darkTheme = true, dynamicColor = false) {
                tertiary = MaterialTheme.colorScheme.tertiary
            }
        }
        composeRule.waitForIdle()
        assertEquals(DarkColors.tertiary, tertiary)
    }

    @Test
    fun lightAndDark_primaryColors_areDifferent() {
        assertNotEquals(
            "Light and dark primary must differ (saffron dark vs light tone)",
            LightColors.primary,
            DarkColors.primary,
        )
        assertNotEquals(LightColors.background, DarkColors.background)
    }

    // ── Shape wiring ──────────────────────────────────────────────────────────

    @Test
    fun theme_shapes_areWired() {
        var largeShape: androidx.compose.ui.graphics.Shape? = null
        composeRule.setContent {
            PaperkeepTheme(darkTheme = false, dynamicColor = false) {
                largeShape = MaterialTheme.shapes.large
            }
        }
        composeRule.waitForIdle()
        // Verify shapes is non-null (MaterialTheme.shapes is always set when PaperkeepShapes is wired)
        assertEquals(PaperkeepShapes.large, largeShape)
    }

    // ── Dynamic color fallback ────────────────────────────────────────────────

    @Test
    fun dynamicColorFallback_rendersWithoutCrash() {
        // dynamicColor = true on Robolectric/SDK 33: dynamic color IS available but uses
        // a test-wallpaper color. We just verify no crash and primary is non-Unspecified.
        var primary = Color.Unspecified
        composeRule.setContent {
            PaperkeepTheme(darkTheme = false, dynamicColor = true) {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        composeRule.waitForIdle()
        assertNotEquals("Dynamic theme must produce a non-Unspecified primary", Color.Unspecified, primary)
    }

    @Test
    fun allColorRoles_areNonDefault_lightTheme() {
        var scheme: androidx.compose.material3.ColorScheme? = null
        composeRule.setContent {
            PaperkeepTheme(darkTheme = false, dynamicColor = false) {
                scheme = MaterialTheme.colorScheme
            }
        }
        composeRule.waitForIdle()
        val s = checkNotNull(scheme)
        assertNotEquals(Color.Unspecified, s.primary)
        assertNotEquals(Color.Unspecified, s.secondary)
        assertNotEquals(Color.Unspecified, s.tertiary)
        assertNotEquals(Color.Unspecified, s.background)
        assertNotEquals(Color.Unspecified, s.error)
        assertNotEquals(Color.Unspecified, s.surface)
    }

    @Test
    fun allColorRoles_areNonDefault_darkTheme() {
        var scheme: androidx.compose.material3.ColorScheme? = null
        composeRule.setContent {
            PaperkeepTheme(darkTheme = true, dynamicColor = false) {
                scheme = MaterialTheme.colorScheme
            }
        }
        composeRule.waitForIdle()
        val s = checkNotNull(scheme)
        assertNotEquals(Color.Unspecified, s.primary)
        assertNotEquals(Color.Unspecified, s.secondary)
        assertNotEquals(Color.Unspecified, s.tertiary)
        assertNotEquals(Color.Unspecified, s.background)
        assertNotEquals(Color.Unspecified, s.error)
    }
}
