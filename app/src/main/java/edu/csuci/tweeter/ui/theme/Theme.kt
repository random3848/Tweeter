package edu.csuci.tweeter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Theme of the app, taken from the designs: black and white with gray text, one red accent, and
 * the Manrope typeface.
 *
 * There is only a light color scheme because the designs only show light screens.
 *
 * @param content The UI to theme.
 */
@Composable
fun TweeterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColors, typography = TYPOGRAPHY, content = content)
}

// Roles the designs don't show use the same neutral grays, so Material's default purple never
// appears.
private val lightColors = lightColorScheme(
    primary = INK,
    onPrimary = WHITE,
    primaryContainer = LIGHT_GRAY,
    onPrimaryContainer = INK,
    inversePrimary = WHITE,
    secondary = MUTED_TEXT,
    onSecondary = WHITE,
    secondaryContainer = PALE_GRAY,
    onSecondaryContainer = INK,
    tertiary = ACCENT_RED,
    onTertiary = WHITE,
    tertiaryContainer = PALE_GRAY,
    onTertiaryContainer = INK,
    background = WHITE,
    onBackground = INK,
    surface = WHITE,
    onSurface = INK,
    surfaceVariant = LIGHT_GRAY,
    onSurfaceVariant = MUTED_TEXT,
    // No tint on raised surfaces; the designs are flat white.
    surfaceTint = WHITE,
    inverseSurface = INK,
    inverseOnSurface = WHITE,
    error = ERROR_RED,
    onError = WHITE,
    errorContainer = PALE_GRAY,
    onErrorContainer = ERROR_RED,
    outline = MID_GRAY,
    outlineVariant = DIVIDER_GRAY,
    surfaceBright = WHITE,
    surfaceDim = LIGHT_GRAY,
    surfaceContainerLowest = WHITE,
    surfaceContainerLow = WHITE,
    surfaceContainer = WHITE,
    surfaceContainerHigh = PALE_GRAY,
    surfaceContainerHighest = LIGHT_GRAY
)
