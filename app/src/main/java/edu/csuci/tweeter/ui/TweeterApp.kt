@file:Suppress("ktlint:standard:kdoc")

package edu.csuci.tweeter.ui

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.auth.CreateAccountRoute
import edu.csuci.tweeter.ui.auth.LogInRoute
import edu.csuci.tweeter.ui.auth.addCreateAccountScreen
import edu.csuci.tweeter.ui.auth.addLogInScreen
import edu.csuci.tweeter.ui.feed.FeedRoute
import edu.csuci.tweeter.ui.feed.addFeedScreen
import edu.csuci.tweeter.ui.map.MapRoute
import edu.csuci.tweeter.ui.map.MapScreen
import edu.csuci.tweeter.ui.map.addMapScreen
import edu.csuci.tweeter.ui.navigation.BottomBarItem
import edu.csuci.tweeter.ui.navigation.LocalMapCover
import edu.csuci.tweeter.ui.navigation.MapCover
import edu.csuci.tweeter.ui.navigation.TweeterBottomBar
import edu.csuci.tweeter.ui.post.PostRoute
import edu.csuci.tweeter.ui.post.addPostScreen
import edu.csuci.tweeter.ui.profile.EditProfileRoute
import edu.csuci.tweeter.ui.profile.ProfileButton
import edu.csuci.tweeter.ui.profile.ProfileRoute
import edu.csuci.tweeter.ui.profile.addEditProfileScreen
import edu.csuci.tweeter.ui.profile.addProfileScreen

/**
 * Bottom-bar items in display order.
 * A non-null route connects an item to its registered screen.
 */
@VisibleForTesting
internal val BOTTOM_BAR_ITEMS: List<BottomBarItem> = listOf(
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
 * The whole app: the screen the user is on, which draws its own top bar, above the bottom bar.
 *
 * Feed is the screen the app opens to. To add a screen with its own tab:
 * 1. In its own package, `ui/<feature>/`, add the screen, which draws its own top bar, a
 *    `@Serializable` route object, and a `NavGraphBuilder.add<Feature>Screen()` function that
 *    registers the screen under the route with `opaqueComposable`; `ui/feed/FeedRoute.kt` is the
 *    example
 * 2. Call that function in the NavHost below
 * 3. Set the matching item's route in [BOTTOM_BAR_ITEMS]
 *    to the feature's route object.
 *
 * Do all three in the same pull request, so no tab ever opens an empty screen. Screens should
 * not edit this file beyond those two lines; keep everything else in the feature's package
 *
 * Profile is not a tab: Feed's profile button opens it, and Home stays selected while it is open.
 * Every screen draws its own top bar, [edu.csuci.tweeter.ui.navigation.TweeterTopBar] on the tabs'
 * screens and [edu.csuci.tweeter.ui.navigation.BackTopBar] on screens opened from them, so a bar
 * comes and goes with its screen. Profile and Post open Log in and Create account for guests, and
 * the tab they were opened from stays selected. Profile's "Edit profile" button opens Edit
 * profile, which hides the bottom bar too.
 *
 * A tab left open keeps its state, but only the open screen is composed, with one exception: once
 * Map has been opened, the map stays composed under the NavHost, so that it is drawn and ready the
 * moment the user switches back to it. Until then it isn't composed at all, so the app doesn't
 * create it or load its tiles for users who never open Map. Every other screen hides it with a
 * background of its own (see `opaqueComposable`), and Map's route draws nothing over it. The map
 * counts as shown while the NavHost composes none of those screens, which follows what the
 * NavHost draws rather than the back stack, which changes a few frames earlier.
 *
 * @param modifier Applied to the whole app
 */
@Composable
fun TweeterApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val mapCover = remember { MapCover() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val isOnMap = destination?.hasRoute(MapRoute::class) == true
    // Set as Map first opens, and kept, also when Android ends the app in the background.
    var hasOpenedMap by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isOnMap) {
        if (isOnMap) {
            hasOpenedMap = true
        }
    }
    val isOnAuth = destination?.hasRoute(LogInRoute::class) == true ||
        destination?.hasRoute(CreateAccountRoute::class) == true
    val isOnEditProfile = destination?.hasRoute(EditProfileRoute::class) == true
    // Log in and Create account keep the tab of the screen that opened them selected.
    val tabDestination = if (isOnAuth) {
        navController.previousBackStackEntry?.destination
    } else {
        destination
    }
    val selectedIndex = selectedTabIndex(BOTTOM_BAR_ITEMS, tabDestination)
    val openLogIn = {
        navController.navigate(LogInRoute) {
            launchSingleTop = true
        }
    }
    val openCreateAccount = {
        navController.navigate(CreateAccountRoute) {
            launchSingleTop = true
        }
    }
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (!isOnEditProfile) {
                TweeterBottomBar(
                    items = BOTTOM_BAR_ITEMS,
                    selectedIndex = selectedIndex,
                    onSelect = { index ->
                        val route = BOTTOM_BAR_ITEMS[index].route
                        if (route != null) {
                            if (index == selectedIndex) {
                                navController.returnToTab(route)
                            } else {
                                navController.navigateToTopLevel(route)
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        // innerPadding includes the status bar; consuming it stops each screen's own top bar from
        // padding below the status bar a second time.
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            CompositionLocalProvider(LocalMapCover provides mapCover) {
                if (hasOpenedMap || isOnMap) {
                    MapScreen(
                        isShown = !mapCover.hidesMap,
                        onShowInFeed = { navController.navigateToFeed() }
                    )
                }
                TweeterNavHost(navController, openLogIn, openCreateAccount)
            }
        }
    }
}

/**
 * The NavHost of every screen, which opens to Feed.
 *
 * @param openLogIn Opens Log in, for screens that ask guests to log in.
 * @param openCreateAccount Opens Create account, for those screens too.
 */
@Composable
private fun TweeterNavHost(
    navController: NavHostController,
    openLogIn: () -> Unit,
    openCreateAccount: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = FeedRoute,
        // Screens switch without fading: a fade keeps the screen being left on view for its whole
        // length.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }
    ) {
        addFeedScreen(
            onShowOnMap = { navController.navigateToTopLevel(MapRoute) },
            topBarActions = {
                ProfileButton(
                    onClick = {
                        navController.navigate(ProfileRoute) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        )
        addPostScreen(onLogIn = openLogIn, onCreateAccount = openCreateAccount)
        addMapScreen()
        addProfileScreen(
            onBack = { navController.navigateUp() },
            onLogIn = openLogIn,
            onCreateAccount = openCreateAccount,
            onEditProfile = {
                navController.navigate(EditProfileRoute) {
                    launchSingleTop = true
                }
            }
        )
        addEditProfileScreen(onClose = { navController.navigateUp() })
        addLogInScreen(
            onBack = { navController.navigateUp() },
            onAuthenticated = { navController.popBackStack() }
        )

        addCreateAccountScreen(
            onBack = { navController.navigateUp() },
            onAuthenticated = { navController.popBackStack() }
        )
    }
}

/**
 * Opens the Home tab on the feed itself, closing any screen opened from it, such as Profile or
 * Log in, which the tab would otherwise restore over the feed.
 */
private fun NavHostController.navigateToFeed() {
    navigateToTopLevel(FeedRoute)
    returnToTab(FeedRoute)
}

/**
 * Switches to the tab of [route], keeping the screens opened in each tab while another is open,
 * without stacking a second copy of a screen that is already on top.
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

/**
 * Shows the screen the tab of [route] opens to, closing the screens opened from it, such as
 * Profile on Home or Log in on Post: what tapping the tab that is already selected does.
 * [navigateToTopLevel] would restore those screens instead.
 */
private fun NavHostController.returnToTab(route: Any) {
    popBackStack(route, inclusive = false)
}
