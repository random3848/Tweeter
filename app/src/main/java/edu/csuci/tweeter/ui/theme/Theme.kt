package edu.csuci.tweeter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import edu.csuci.tweeter.data.ThemeRepository

/** Whether the app is dark now: following the user's theme choice (Light or Dark). */
@Composable
fun isDarkTheme(repository: ThemeRepository): Boolean {
    val choice by repository.themeChoice.collectAsState()
    return choice == ThemeChoice.DARK
}

/**
 * Theme of the app, taken from the designs: black and white with gray text, one red accent, and
 * the Manrope typeface.
 *
 * The dark color scheme mirrors the light one; the designs only show light screens, so its colors
 * are our own.
 *
 * @param darkTheme Whether to use the dark colors.
 * @param content The UI to theme.
 */
@Composable
fun TweeterTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColors else lightColors,
        typography = TYPOGRAPHY,
        content = content
    )
}

// Roles the designs don't show use the same neutral grays, so Material's default purple never
// appears.
private val lightColors = lightColorScheme(
    primary = BLUE,
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

private val darkColors = darkColorScheme(
    primary = DARK_TEXT,
    onPrimary = INK,
    primaryContainer = DARK_LIGHT_GRAY,
    onPrimaryContainer = DARK_TEXT,
    inversePrimary = INK,
    secondary = DARK_MUTED_TEXT,
    onSecondary = INK,
    secondaryContainer = DARK_PALE_GRAY,
    onSecondaryContainer = DARK_TEXT,
    tertiary = ACCENT_RED,
    onTertiary = WHITE,
    tertiaryContainer = DARK_PALE_GRAY,
    onTertiaryContainer = DARK_TEXT,
    background = DARK_SURFACE,
    onBackground = DARK_TEXT,
    surface = DARK_SURFACE,
    onSurface = DARK_TEXT,
    surfaceVariant = DARK_LIGHT_GRAY,
    onSurfaceVariant = DARK_MUTED_TEXT,
    // No tint on raised surfaces, same as the light scheme.
    surfaceTint = DARK_SURFACE,
    inverseSurface = DARK_TEXT,
    inverseOnSurface = INK,
    error = DARK_ERROR_RED,
    onError = INK,
    errorContainer = DARK_PALE_GRAY,
    onErrorContainer = DARK_ERROR_RED,
    outline = MID_GRAY,
    outlineVariant = DARK_DIVIDER_GRAY,
    surfaceBright = DARK_LIGHT_GRAY,
    surfaceDim = DARK_SURFACE,
    surfaceContainerLowest = DARK_SURFACE,
    surfaceContainerLow = DARK_SURFACE,
    surfaceContainer = DARK_SURFACE,
    surfaceContainerHigh = DARK_PALE_GRAY,
    surfaceContainerHighest = DARK_LIGHT_GRAY
)
