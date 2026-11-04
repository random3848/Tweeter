@file:Suppress("ktlint:standard:kdoc")

package edu.csuci.tweeter.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.feed.FeedRoute
import edu.csuci.tweeter.ui.feed.addFeedScreen
import edu.csuci.tweeter.ui.map.MapRoute
import edu.csuci.tweeter.ui.map.addMapScreen
import edu.csuci.tweeter.ui.navigation.BottomBarItem
import edu.csuci.tweeter.ui.navigation.TweeterBottomBar
import edu.csuci.tweeter.ui.navigation.TweeterTopBar
import edu.csuci.tweeter.ui.post.PostRoute
import edu.csuci.tweeter.ui.post.addPostScreen
import edu.csuci.tweeter.ui.profile.ProfileButton
import edu.csuci.tweeter.ui.profile.ProfileRoute
import edu.csuci.tweeter.ui.profile.addProfileScreen

/**
 * Bottom-bar items in display order.
 * A non-null route connects an item to its registered screen.
 */
private val BOTTOM_BAR_ITEMS: List<BottomBarItem> = listOf(
    BottomBarItem(
        selectedIcon = R.drawable.ic_home_filled,
        unselectedIcon = R.drawable.ic_home_unfilled,
        description = R.string.navigation_home,
        route = FeedRoute
    ),
    BottomBarItem(
        selectedIcon = R.drawable.ic_upload_filled,
        unselectedIcon = R.drawable.ic_upload_unfilled,
        description = R.string.navigation_create_post,
        route = PostRoute
    ),
    BottomBarItem(
        selectedIcon = R.drawable.ic_map_filled,
        unselectedIcon = R.drawable.ic_map_unfilled,
        description = R.string.navigation_map,
        route = MapRoute
    )
)

/**
 * The whole app: the top bar, the bottom bar, and the screen the user is on between them.
 *
 * Feed is the screen the app opens to. To add a screen with its own tab:
 * 1. In its own package, `ui/<feature>/`, add the screen, a `@Serializable` route object, and a
 *    `NavGraphBuilder.add<Feature>Screen()` function that registers the screen under the route
 *    `ui/feed/FeedRoute.kt` is the example
 * 2. Call that function in the NavHost below
 * 3. Set the matching item's route in [BOTTOM_BAR_ITEMS]
 *    to the feature's route object.
 *
 * Do all three in the same pull request, so no tab ever opens an empty screen. Screens should
 * not edit this file beyond those two lines; keep everything else in the feature's package
 *
 * Profile is not a tab: Feed's profile button opens it, and Home stays selected while it is open.
 * It draws its own top bar with a back arrow, so the shared one is hidden there.
 *
 * @param modifier Applied to the whole app
 */
@Composable
fun TweeterApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val isOnFeed = destination?.hasRoute(FeedRoute::class) == true
    val isOnProfile = destination?.hasRoute(ProfileRoute::class) == true
    Scaffold(
        modifier = modifier,
        topBar = {
            if (!isOnProfile) {
                TweeterTopBar(
                    actions = {
                        if (isOnFeed) {
                            ProfileButton(
                                onClick = {
                                    navController.navigate(ProfileRoute) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            TweeterBottomBar(
                items = BOTTOM_BAR_ITEMS,
                isSelected = { item ->
                    val route = item.route
                    when {
                        route == null -> false

                        // Profile is opened from Feed, so Home stays selected on it.
                        route == FeedRoute && isOnProfile -> true

                        else -> destination?.hierarchy.orEmpty().any { it.hasRoute(route::class) }
                    }
                },
                onSelect = { route ->
                    navController.navigateToTopLevel(route)
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FeedRoute,
            // Without the shared top bar, innerPadding already includes the status bar; consuming
            // it stops a screen's own top bar from padding below the status bar a second time.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            addFeedScreen()
            addPostScreen()
            addMapScreen()
            addProfileScreen(onBack = { navController.navigateUp() })
        }
    }
}

/**
 * Switches tabs while preserving navigation state and avoiding duplicate destinations.
 * to Feed, tapping the current tab does nothing, and each tab keeps its state while another one
 * is open.
 */
private fun NavHostController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
