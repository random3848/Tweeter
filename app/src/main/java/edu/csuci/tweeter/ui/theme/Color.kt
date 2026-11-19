package edu.csuci.tweeter.ui.theme

import androidx.compose.ui.graphics.Color

// Colors sampled from the Tweeter designs (Feed, Map, New post and Settings). Screens use them
// through MaterialTheme.colorScheme, which Theme.kt builds from them.

/** Text, icons, and filled buttons, chips and switches. */
val INK = Color(0xFF111614)

/** Filled buttons, and the Tweeter logo. */
val BLUE = Color(0xFF174A5D)

/** Screens, cards, and the bars at the top and bottom. */
val WHITE = Color(0xFFFFFFFF)

/** Secondary text: locations, timestamps, section labels and hints. */
val MUTED_TEXT = Color(0xFF6B716D)

/** Tonal buttons such as "Edit profile". */
val PALE_GRAY = Color(0xFFEEF1EE)

/** Avatar placeholders and the round profile button. */
val LIGHT_GRAY = Color(0xFFE4E7E4)

/** Dividers between rows and sections. */
val DIVIDER_GRAY = Color(0xFFECEEEC)

/** A switch that is off. */
val MID_GRAY = Color(0xFF8E958F)

/** The accent: a liked heart, the selected photo, the highlighted map pin. */
val ACCENT_RED = Color(0xFFE2412D)

/** Destructive actions such as "Log out", and errors. */
val ERROR_RED = Color(0xFFC42F1D)


// Dark theme counterparts of the colors above, used by Theme.kt when dark mode is on.
/** Screens and the bars at the top and bottom. */
val DARK_SURFACE = Color(0xFF0E1A1F)

/** Text and icons. */
val DARK_TEXT = Color(0xFFF4F6F5)

/** Lighter BLUE for buttons and accents; the original is too dark on a dark screen. */
val DARK_BLUE = Color(0xFF6FB3CC)

/** Secondary text. Same as DARK_TEXT, so all dark mode text is one color. */
val DARK_MUTED_TEXT = DARK_TEXT

/** Tonal buttons. */
val DARK_PALE_GRAY = Color(0xFF182830)

/** Avatar placeholders and the round profile button. */
val DARK_LIGHT_GRAY = Color(0xFF22343D)

/** Dividers between rows and sections. */
val DARK_DIVIDER_GRAY = Color(0xFF1B2A31)

/** Lighter ERROR_RED for contrast on dark. */
val DARK_ERROR_RED = Color(0xFFFF6B5A)

/** The bars at the top and bottom, slightly lighter than the screen. */
val DARK_BAR = Color(0xFF0F2B36)
