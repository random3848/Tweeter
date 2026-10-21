package edu.csuci.tweeter.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * A screen that has its own tab in the bottom bar.
 *
 * Each feature declares its destination in its own package and adds it to the list in
 * `TweeterApp` in the same change that builds the screen, so no tab ever opens an empty screen.
 */
interface TopLevelDestination {
    /** The `@Serializable` route object the screen is registered under in the NavHost. */
    val route: Any

    /** The filled icon shown while this tab is selected. */
    @get:DrawableRes
    val selectedIcon: Int

    /** The outlined icon shown while another tab is selected. */
    @get:DrawableRes
    val unselectedIcon: Int

    /** What the tab does, read by screen readers since the tab has no visible label. */
    @get:StringRes
    val contentDescription: Int
}
