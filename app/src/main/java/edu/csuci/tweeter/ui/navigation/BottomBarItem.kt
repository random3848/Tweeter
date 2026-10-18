package edu.csuci.tweeter.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * A bottom-bar item with an optional connection to a working screen.
 *
 * Holds an icon and accessibility label. Connecting a working destination
 * enables the item.
 *
 * @property icon The icon displayed before a destination is connected.
 * @property description The accessibility label before a destination is connected.
 * @property destination The working destination; null keeps the item disabled.
 */

class BottomBarItem(
    @get:DrawableRes val icon: Int,
    @get:StringRes val description: Int,
    val destination: TopLevelDestination? = null
)
