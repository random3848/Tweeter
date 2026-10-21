package edu.csuci.tweeter.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

/**
 * A bottom-bar item with an optional connection to a working screen.
 *
 * Items without a screen remain visible but disabled.
 *
 * @property selectedIcon The filled icon displayed when this item is selected.
 * @property unselectedIcon The outlined icon displayed otherwise.
 * @property description The accessibility label for this item.
 * @property screen The screen this item opens; null keeps this item disabled.
 */

data class BottomBarItem(
    @get:DrawableRes val selectedIcon: Int,
    @get:DrawableRes val unselectedIcon: Int,
    @get:StringRes val description: Int,
    val screen: (@Composable () -> Unit)? = null
)
