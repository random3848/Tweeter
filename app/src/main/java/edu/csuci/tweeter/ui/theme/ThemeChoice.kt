package edu.csuci.tweeter.ui.theme

import androidx.annotation.StringRes
import edu.csuci.tweeter.R

/**
 * The user's dark mode choice: light or dark mode.
 *
 * @property labelRes The choice's name on its selector button.
 */
enum class ThemeChoice(@get:StringRes val labelRes: Int) {
    /** Force light mode. */
    LIGHT(R.string.theme_light),

    /** Force dark mode. */
    DARK(R.string.theme_dark)
}
