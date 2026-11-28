package edu.csuci.tweeter.ui.map

import androidx.annotation.StringRes
import edu.csuci.tweeter.R

/**
 * Whose posts the map pins, as a logged-in user chooses above it.
 *
 * @property label What the choice is called on screen.
 */
enum class PinFilter(@StringRes val label: Int) {
    /** Every post. */
    ALL(R.string.map_filter_all),

    /** The posts of the users the user follows. */
    FOLLOWING(R.string.map_filter_following),

    /** The user's own posts. */
    MINE(R.string.map_filter_mine)
}
