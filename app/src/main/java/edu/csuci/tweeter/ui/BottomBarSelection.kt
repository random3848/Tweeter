package edu.csuci.tweeter.ui

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import edu.csuci.tweeter.ui.navigation.BottomBarItem

/**
 * The screens on this controller's back stack, bottom first: its composable destinations, without
 * the graphs holding them, as [selectedTabIndex] takes them. None until the NavHost has set its
 * graph, which attaches the navigator the screens are on.
 */
internal fun NavController.screenBackStack(): List<NavBackStackEntry> {
    val composeNavigator = navigatorProvider.getNavigator(ComposeNavigator::class.java)
    return if (composeNavigator.isAttached) composeNavigator.backStack.value else emptyList()
}

/**
 * Returns the index in [items] of the tab to show selected while [backStack] is open, or null
 * when no tab is.
 *
 * [backStack] is the destinations of the back stack, bottom first. A screen opened from a tab,
 * such as Profile from Home, a profile from Search, or Log in from Post, keeps that tab selected:
 * the selected tab is that of the topmost screen that is a tab's.
 */
internal fun selectedTabIndex(items: List<BottomBarItem>, backStack: List<NavDestination>): Int? {
    for (destination in backStack.asReversed()) {
        val index = tabIndexOf(items, destination)
        if (index != null) {
            return index
        }
    }
    return null
}

// The index in [items] of the tab [destination] is the screen of, or null if it's no tab's.
private fun tabIndexOf(items: List<BottomBarItem>, destination: NavDestination): Int? {
    for (index in items.indices) {
        val route = items[index].route ?: continue
        if (isWithin(destination, route)) {
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
