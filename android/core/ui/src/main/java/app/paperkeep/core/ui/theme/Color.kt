package app.paperkeep.core.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand anchor ──────────────────────────────────────────────────────────────
// Saffron — the single confident accent that differentiates Paperkeep from the
// cold-blue scanner apps. Used for the FAB, CTAs, and edge-detection overlays.
// Dynamic color (Android 12+) overrides this with wallpaper-derived tones.
val Saffron = Color(0xFFF59E0B)

// ── Material 3 tonal palette derived from Saffron #F59E0B ────────────────────
// Generated via the M3 tone mapping spec (hue-preserving palette).
// Light-theme primary is a dark tone for ≥ 4.5:1 contrast on white.
// Dark-theme primary is a light tone for ≥ 4.5:1 contrast on dark surfaces.

object LightColors {
    // Primary — dark saffron for contrast on white (#FFFFFF)
    // Contrast: #7A4F00 on white ≈ 7.8:1 ✓ AA + AAA
    val primary              = Color(0xFF7A4F00)
    val onPrimary            = Color(0xFFFFFFFF)
    val primaryContainer     = Color(0xFFFFDDB3)   // light saffron tint
    val onPrimaryContainer   = Color(0xFF271900)   // near-black

    // Secondary — warm brown/sand
    val secondary            = Color(0xFF6F5B40)
    val onSecondary          = Color(0xFFFFFFFF)
    val secondaryContainer   = Color(0xFFFADEBC)
    val onSecondaryContainer = Color(0xFF271806)

    // Tertiary — muted olive for document-context accents
    val tertiary             = Color(0xFF4D6645)
    val onTertiary           = Color(0xFFFFFFFF)
    val tertiaryContainer    = Color(0xFFCFECC4)
    val onTertiaryContainer  = Color(0xFF0A2008)

    // Surface / background — warm near-white
    val surface              = Color(0xFFFFF8F2)
    val onSurface            = Color(0xFF1F1B16)
    val surfaceVariant       = Color(0xFFF0E0CF)
    val onSurfaceVariant     = Color(0xFF4F4539)
    val surfaceContainerHigh = Color(0xFFEDE0D4)
    val background           = Color(0xFFFFF8F2)
    val onBackground         = Color(0xFF1F1B16)

    // Error
    val error                = Color(0xFFBA1A1A)
    val onError              = Color(0xFFFFFFFF)
    val errorContainer       = Color(0xFFFFDAD6)
    val onErrorContainer     = Color(0xFF410002)

    // Outline
    val outline              = Color(0xFF817567)
    val outlineVariant       = Color(0xFFD3C4B4)
}

object DarkColors {
    // Primary — light saffron for contrast on dark surfaces
    // Contrast: #FFB951 on #17120C ≈ 8.1:1 ✓ AA + AAA
    val primary              = Color(0xFFFFB951)
    val onPrimary            = Color(0xFF412D00)
    val primaryContainer     = Color(0xFF5D4200)
    val onPrimaryContainer   = Color(0xFFFFDDB3)

    // Secondary
    val secondary            = Color(0xFFDDC2A1)
    val onSecondary          = Color(0xFF3E2D16)
    val secondaryContainer   = Color(0xFF55432B)
    val onSecondaryContainer = Color(0xFFFADEBC)

    // Tertiary
    val tertiary             = Color(0xFFB4D0A9)
    val onTertiary           = Color(0xFF20371B)
    val tertiaryContainer    = Color(0xFF364E30)
    val onTertiaryContainer  = Color(0xFFCFECC4)

    // Surface / background — very dark warm tone
    val surface              = Color(0xFF17120C)
    val onSurface            = Color(0xFFEBE0D4)
    val surfaceVariant       = Color(0xFF4F4539)
    val onSurfaceVariant     = Color(0xFFD3C4B4)
    val surfaceContainerHigh = Color(0xFF2C2419)
    val background           = Color(0xFF17120C)
    val onBackground         = Color(0xFFEBE0D4)

    // Error
    val error                = Color(0xFFFFB4AB)
    val onError              = Color(0xFF690005)
    val errorContainer       = Color(0xFF93000A)
    val onErrorContainer     = Color(0xFFFFDAD6)

    // Outline
    val outline              = Color(0xFF9C8E7E)
    val outlineVariant       = Color(0xFF4F4539)
}
