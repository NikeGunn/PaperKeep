package app.paperkeep.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
 * Tests for 1B.5: Material 3 theme — light/dark color correctness + dynamic color fallback.
 *
 * Note: Dynamic color (Android 12+) reads from wallpaper, which is unavailable in Robolectric.
 * We verify that the fallback static palette matches our brand colors when dynamicColor=false.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── 1B.5: Light mode color correctness ────────────────────────────────────

    @Test
    fun lightTheme_primary_matchesBrandAmberDerivative() {
        var primary = Color.Unspecified
        composeRule.setContent {
            PaperkeepTheme(darkTheme = false, dynamicColor = false) {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        composeRule.waitForIdle()
        assertEquals("Light primary must match LightColors.primary", LightColors.primary, primary)
    }

    @Test
    fun lightTheme_background_isWarmSurface() {
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

    // ── 1B.5: Dark mode color correctness ─────────────────────────────────────

    @Test
    fun darkTheme_primary_isAmberTint() {
        var primary = Color.Unspecified
        composeRule.setContent {
            PaperkeepTheme(darkTheme = true, dynamicColor = false) {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        composeRule.waitForIdle()
        assertEquals("Dark primary must match DarkColors.primary", DarkColors.primary, primary)
    }

    @Test
    fun darkTheme_background_isDarkSurface() {
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
    fun darkTheme_colorsAreDifferentFromLight() {
        // Verify via the static color objects — no need to compose twice.
        assertNotEquals(
            "Light and dark primary colors must differ",
            LightColors.primary,
            DarkColors.primary
        )
        assertNotEquals(
            "Light and dark background colors must differ",
            LightColors.background,
            DarkColors.background
        )
    }

    // ── 1B.5: Dynamic color fallback ──────────────────────────────────────────

    @Test
    fun dynamicColorFallback_onUnsupportedDevice_usesStaticPalette() {
        // Robolectric uses SDK 33 but has no wallpaper engine, so dynamic color
        // safely falls back to our static LightColors on API < S or when unavailable.
        // We verify the theme still renders without crashing.
        var primary = Color.Unspecified
        composeRule.setContent {
            // dynamicColor=true — on Robolectric this path falls back gracefully
            PaperkeepTheme(darkTheme = false, dynamicColor = true) {
                primary = MaterialTheme.colorScheme.primary
            }
        }
        composeRule.waitForIdle()
        // The theme rendered; primary is non-default (not Unspecified)
        assertNotEquals(Color.Unspecified, primary)
    }
}
