package com.example.veyra.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

internal val VeyraDarkColorScheme: ColorScheme = darkColorScheme(
    primary = VeyraPalette.Champagne,
    onPrimary = VeyraPalette.Charcoal,
    primaryContainer = VeyraPalette.Smoke,
    onPrimaryContainer = VeyraPalette.Champagne,
    secondary = VeyraPalette.Stone,
    onSecondary = VeyraPalette.Charcoal,
    secondaryContainer = VeyraPalette.Slate,
    onSecondaryContainer = VeyraPalette.Ivory,
    tertiary = VeyraPalette.Champagne,
    onTertiary = VeyraPalette.Charcoal,
    background = VeyraPalette.Charcoal,
    onBackground = VeyraPalette.Ivory,
    surface = VeyraPalette.Charcoal,
    onSurface = VeyraPalette.Ivory,
    surfaceVariant = VeyraPalette.Slate,
    onSurfaceVariant = VeyraPalette.Stone,
    surfaceContainerLowest = VeyraPalette.Charcoal,
    surfaceContainerLow = VeyraPalette.Graphite,
    surfaceContainer = VeyraPalette.Graphite,
    surfaceContainerHigh = VeyraPalette.Slate,
    surfaceContainerHighest = VeyraPalette.Smoke,
    surfaceBright = VeyraPalette.Smoke,
    surfaceDim = VeyraPalette.Charcoal,
    outline = VeyraPalette.Pebble,
    outlineVariant = VeyraPalette.Ash,
    inverseSurface = VeyraPalette.Ivory,
    inverseOnSurface = VeyraPalette.Charcoal,
    inversePrimary = VeyraPalette.Bronze,
    error = VeyraPalette.Error,
    onError = VeyraPalette.OnError,
    scrim = VeyraPalette.Charcoal,
)

// Provisional light scheme so the theme structure already supports both modes.
// It is not exposed in the UI yet; the light theme is designed in Phase 8.
internal val VeyraLightColorScheme: ColorScheme = lightColorScheme(
    primary = VeyraPalette.Bronze,
    onPrimary = VeyraPalette.Ivory,
    primaryContainer = VeyraPalette.Sand,
    onPrimaryContainer = VeyraPalette.Charcoal,
    secondary = VeyraPalette.Pebble,
    onSecondary = VeyraPalette.Ivory,
    background = VeyraPalette.Ivory,
    onBackground = VeyraPalette.Charcoal,
    surface = VeyraPalette.Ivory,
    onSurface = VeyraPalette.Charcoal,
    surfaceVariant = VeyraPalette.Sand,
    onSurfaceVariant = VeyraPalette.Ash,
    outline = VeyraPalette.Pebble,
    inverseSurface = VeyraPalette.Charcoal,
    inverseOnSurface = VeyraPalette.Ivory,
    inversePrimary = VeyraPalette.Champagne,
)

/**
 * Veyra's Material 3 theme. Dark is the default and, for now, the only mode the app uses.
 * Dynamic (wallpaper) colour is intentionally not supported: the brand palette always wins.
 */
@Composable
fun VeyraTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) VeyraDarkColorScheme else VeyraLightColorScheme,
        typography = VeyraTypography,
        shapes = VeyraShapes,
        content = content,
    )
}
