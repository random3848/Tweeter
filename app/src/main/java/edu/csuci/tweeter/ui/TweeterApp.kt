package edu.csuci.tweeter.ui

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
import edu.csuci.tweeter.ui.feed.FeedRoute
import edu.csuci.tweeter.ui.feed.feedScreen
import edu.csuci.tweeter.ui.navigation.TopLevelDestination
import edu.csuci.tweeter.ui.navigation.TweeterBottomBar
import edu.csuci.tweeter.ui.navigation.TweeterTopBar

/**
 * The tabs of the bottom bar, in order. A feature adds its destination here in the same change
 * that builds its screen; the bar stays hidden until the first one is added.
 */
private val TOP_LEVEL_DESTINATIONS: List<TopLevelDestination> = emptyList()

/**
 * The whole app: the top bar, the bottom bar, and the screen the user is on between them.
 *
 * Feed is the screen the app opens to. Each feature registers its screen in the NavHost below
 * with the `NavGraphBuilder` function from its own package.
 *
 * @param modifier Applied to the whole app.
 */
@Composable
fun TweeterApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    Scaffold(
        modifier = modifier,
        topBar = { TweeterTopBar() },
        bottomBar = {
            if (TOP_LEVEL_DESTINATIONS.isNotEmpty()) {
                TweeterBottomBar(
                    destinations = TOP_LEVEL_DESTINATIONS,
                    isSelected = { destination ->
                        backStackEntry?.destination?.hierarchy.orEmpty().any {
                            it.hasRoute(destination.route::class)
                        }
                    },
                    onSelect = { navController.navigateToTopLevel(it) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FeedRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            feedScreen()
        }
    }
}

/**
 * Switches to [destination]'s tab the way tabs are expected to work: back from any tab returns
 * to Feed, tapping the current tab does nothing, and each tab keeps its state while another one
 * is open.
 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
