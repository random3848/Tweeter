package edu.csuci.tweeter.ui.theme

import androidx.compose.ui.graphics.Color

// Colors sampled from the Tweeter designs (Feed, Map, New post and Settings). Screens use them
// through MaterialTheme.colorScheme, which Theme.kt builds from them.

/** Text, icons, and filled buttons, chips and switches. */
val INK = Color(0xFF111614)

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
