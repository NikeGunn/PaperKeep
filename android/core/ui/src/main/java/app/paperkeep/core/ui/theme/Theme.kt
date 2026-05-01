package app.paperkeep.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val paperkeepLightColorScheme = lightColorScheme(
    primary              = LightColors.primary,
    onPrimary            = LightColors.onPrimary,
    primaryContainer     = LightColors.primaryContainer,
    onPrimaryContainer   = LightColors.onPrimaryContainer,
    secondary            = LightColors.secondary,
    onSecondary          = LightColors.onSecondary,
    secondaryContainer   = LightColors.secondaryContainer,
    onSecondaryContainer = LightColors.onSecondaryContainer,
    tertiary             = LightColors.tertiary,
    onTertiary           = LightColors.onTertiary,
    tertiaryContainer    = LightColors.tertiaryContainer,
    onTertiaryContainer  = LightColors.onTertiaryContainer,
    surface              = LightColors.surface,
    onSurface            = LightColors.onSurface,
    surfaceVariant       = LightColors.surfaceVariant,
    onSurfaceVariant     = LightColors.onSurfaceVariant,
    background           = LightColors.background,
    onBackground         = LightColors.onBackground,
    error                = LightColors.error,
    onError              = LightColors.onError,
    errorContainer       = LightColors.errorContainer,
    onErrorContainer     = LightColors.onErrorContainer,
    outline              = LightColors.outline,
    outlineVariant       = LightColors.outlineVariant,
)

private val paperkeepDarkColorScheme = darkColorScheme(
    primary              = DarkColors.primary,
    onPrimary            = DarkColors.onPrimary,
    primaryContainer     = DarkColors.primaryContainer,
    onPrimaryContainer   = DarkColors.onPrimaryContainer,
    secondary            = DarkColors.secondary,
    onSecondary          = DarkColors.onSecondary,
    secondaryContainer   = DarkColors.secondaryContainer,
    onSecondaryContainer = DarkColors.onSecondaryContainer,
    tertiary             = DarkColors.tertiary,
    onTertiary           = DarkColors.onTertiary,
    tertiaryContainer    = DarkColors.tertiaryContainer,
    onTertiaryContainer  = DarkColors.onTertiaryContainer,
    surface              = DarkColors.surface,
    onSurface            = DarkColors.onSurface,
    surfaceVariant       = DarkColors.surfaceVariant,
    onSurfaceVariant     = DarkColors.onSurfaceVariant,
    background           = DarkColors.background,
    onBackground         = DarkColors.onBackground,
    error                = DarkColors.error,
    onError              = DarkColors.onError,
    errorContainer       = DarkColors.errorContainer,
    onErrorContainer     = DarkColors.onErrorContainer,
    outline              = DarkColors.outline,
    outlineVariant       = DarkColors.outlineVariant,
)

// OLED-true-black override: replaces surface/background with pure black for AMOLED panels.
// onSurface stays at the original warm white to maintain ≥ 4.5:1 contrast on #000000.
private val paperkeepOledColorScheme = paperkeepDarkColorScheme.copy(
    surface    = Color(0xFF000000),
    background = Color(0xFF000000),
    surfaceVariant       = Color(0xFF1A1610),
    surfaceContainerHigh = Color(0xFF0D0B08),
)

/**
 * Root theme for all Paperkeep screens.
 *
 * Dynamic color (Android 12+ / API 31+): adapts to the user's wallpaper palette.
 * Falls back to the saffron-derived static palette on older devices or when
 * [dynamicColor] is false.
 *
 * [oledTrueBlack]: overrides surface/background to #000000 for AMOLED panels.
 * Only applied when [darkTheme] is true (pointless on a white AMOLED background).
 *
 * Shape and typography tokens are always applied regardless of dynamic color.
 */
@Composable
fun PaperkeepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    oledTrueBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && oledTrueBlack) base.copy(surface = Color.Black, background = Color.Black)
            else base
        }
        darkTheme && oledTrueBlack -> paperkeepOledColorScheme
        darkTheme -> paperkeepDarkColorScheme
        else      -> paperkeepLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = PaperkeepTypography,
        shapes      = PaperkeepShapes,
        content     = content,
    )
}
