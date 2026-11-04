package edu.csuci.tweeter.ui

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import edu.csuci.tweeter.ui.feed.FeedRoute
import edu.csuci.tweeter.ui.navigation.BottomBarItem
import edu.csuci.tweeter.ui.profile.ProfileRoute

/**
 * Returns the index in [items] of the tab to show selected for [tabDestination], or null when no
 * tab is.
 *
 * [tabDestination] is the screen whose tab stays selected: the open screen, or for Log in and
 * Create account the screen that opened them. Home stays selected while Profile, which opens from
 * Feed, is open.
 */
internal fun selectedTabIndex(items: List<BottomBarItem>, tabDestination: NavDestination?): Int? {
    if (tabDestination == null) {
        return null
    }

    val isOnProfile = tabDestination.hasRoute(ProfileRoute::class)
    for (index in items.indices) {
        val route = items[index].route ?: continue
        if ((isOnProfile && route == FeedRoute) || isWithin(tabDestination, route)) {
            return index
        }
    }
    return null
}

// Whether [destination] or a graph containing it is registered under [route], walking the parents
// instead of allocating a hierarchy sequence.
private fun isWithin(destination: NavDestination, route: Any): Boolean {
    var node: NavDestination? = destination
    while (node != null) {
        if (node.hasRoute(route::class)) {
            return true
        }
        node = node.parent
    }
    return false
}
