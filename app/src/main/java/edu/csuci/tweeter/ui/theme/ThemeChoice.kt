package edu.csuci.tweeter.ui.theme

import androidx.annotation.StringRes
import edu.csuci.tweeter.R

/**
 * The user's dark mode choice: follow the system setting, or force light or dark mode.
 *
 * @property labelRes The choice's name on its selector button.
 */
enum class ThemeChoice(@StringRes val labelRes: Int) {
    /** Follow the system (phone's) dark mode setting. */
    SYSTEM(R.string.theme_system),

    /** Force light mode. */
    LIGHT(R.string.theme_light),

    /** Force dark mode. */
    DARK(R.string.theme_dark)
}
