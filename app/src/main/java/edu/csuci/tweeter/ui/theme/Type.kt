package edu.csuci.tweeter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import edu.csuci.tweeter.R

/** Manrope, the typeface of the designs, from one variable font file (res/font/manrope.ttf). */
private val MANROPE = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold)
)

/** Material's default text styles, used for sizes the designs don't show. */
private val DEFAULT_TYPOGRAPHY = Typography()

/**
 * Text styles of the designs, all in Manrope.
 *
 * - headlineMedium: the "Tweeter" title on the feed.
 * - titleLarge: screen titles in the top bar ("Settings", "New post").
 * - titleMedium: names and item titles ("maya.k", "American robin", "24 likes").
 * - bodyLarge: list rows ("Private account").
 * - bodyMedium: captions and other text.
 * - bodySmall: secondary lines (locations, timestamps, row subtitles).
 * - labelLarge: buttons and chips ("Post", "Edit profile", "Today").
 * - labelMedium: section labels ("Privacy", "Caption").
 */
internal val TYPOGRAPHY = Typography(
    displayLarge = DEFAULT_TYPOGRAPHY.displayLarge.copy(fontFamily = MANROPE),
    displayMedium = DEFAULT_TYPOGRAPHY.displayMedium.copy(fontFamily = MANROPE),
    displaySmall = DEFAULT_TYPOGRAPHY.displaySmall.copy(fontFamily = MANROPE),
    headlineLarge = DEFAULT_TYPOGRAPHY.headlineLarge.copy(fontFamily = MANROPE),
    headlineMedium = TextStyle(
        fontFamily = MANROPE,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = DEFAULT_TYPOGRAPHY.headlineSmall.copy(fontFamily = MANROPE),
    titleLarge = TextStyle(
        fontFamily = MANROPE,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = MANROPE,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = DEFAULT_TYPOGRAPHY.titleSmall.copy(fontFamily = MANROPE),
    bodyLarge = TextStyle(
        fontFamily = MANROPE,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = MANROPE,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = MANROPE,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = MANROPE,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MANROPE,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp
    ),
    labelSmall = DEFAULT_TYPOGRAPHY.labelSmall.copy(fontFamily = MANROPE)
)

/** One weight of the variable Manrope font. */
private fun manrope(weight: FontWeight) = Font(
    R.font.manrope,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)
