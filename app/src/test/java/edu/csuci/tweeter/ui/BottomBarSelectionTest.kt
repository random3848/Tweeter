package edu.csuci.tweeter.ui

import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavigatorProvider
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import edu.csuci.tweeter.ui.auth.CreateAccountRoute
import edu.csuci.tweeter.ui.auth.LogInRoute
import edu.csuci.tweeter.ui.feed.FeedRoute
import edu.csuci.tweeter.ui.map.MapRoute
import edu.csuci.tweeter.ui.post.PostRoute
import edu.csuci.tweeter.ui.profile.EditProfileRoute
import edu.csuci.tweeter.ui.profile.ProfileRoute
import edu.csuci.tweeter.ui.search.SearchRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomBarSelectionTest {
    private val graph: NavGraph = appGraph()

    // Stands in for a screen opened from a tab whose route takes arguments, such as a user's
    // profile or an animal's posts: building those destinations needs Android's Uri, which JVM
    // tests don't have.
    private val openedScreen: NavDestination = ComposeNavigator().createDestination()

    @Test
    fun selectedTabIndex_emptyBackStack_noTab() {
        assertNull(selectedTabIndex(BOTTOM_BAR_ITEMS, backStack = emptyList()))
    }

    @Test
    fun selectedTabIndex_feed_homeTab() {
        assertEquals(FeedRoute, selectedTabRouteOn(screenOf(FeedRoute)))
    }

    @Test
    fun selectedTabIndex_search_searchTab() {
        assertEquals(SearchRoute, selectedTabRouteOn(screenOf(FeedRoute), screenOf(SearchRoute)))
    }

    @Test
    fun selectedTabIndex_post_postTab() {
        assertEquals(PostRoute, selectedTabRouteOn(screenOf(FeedRoute), screenOf(PostRoute)))
    }

    @Test
    fun selectedTabIndex_map_mapTab() {
        assertEquals(MapRoute, selectedTabRouteOn(screenOf(FeedRoute), screenOf(MapRoute)))
    }

    @Test
    fun selectedTabIndex_profileOpenedFromFeed_homeTab() {
        assertEquals(FeedRoute, selectedTabRouteOn(screenOf(FeedRoute), screenOf(ProfileRoute)))
    }

    @Test
    fun selectedTabIndex_screenOpenedFromFeed_homeTab() {
        assertEquals(FeedRoute, selectedTabRouteOn(screenOf(FeedRoute), openedScreen))
    }

    @Test
    fun selectedTabIndex_screenOpenedFromSearch_searchTab() {
        assertEquals(
            SearchRoute,
            selectedTabRouteOn(screenOf(FeedRoute), screenOf(SearchRoute), openedScreen)
        )
    }

    @Test
    fun selectedTabIndex_screensOpenedOneFromAnotherInSearch_searchTab() {
        assertEquals(
            SearchRoute,
            selectedTabRouteOn(
                screenOf(FeedRoute),
                screenOf(SearchRoute),
                openedScreen,
                openedScreen
            )
        )
    }

    @Test
    fun selectedTabIndex_logInOpenedFromPost_postTab() {
        assertEquals(
            PostRoute,
            selectedTabRouteOn(screenOf(FeedRoute), screenOf(PostRoute), screenOf(LogInRoute))
        )
    }

    @Test
    fun selectedTabIndex_logInAlone_noTab() {
        assertNull(selectedTabRouteOn(screenOf(LogInRoute)))
    }

    // The route of the tab selected while [backStack] is open, bottom first, or null for none.
    private fun selectedTabRouteOn(vararg backStack: NavDestination): Any? =
        selectedTabIndex(BOTTOM_BAR_ITEMS, backStack.asList())?.let { BOTTOM_BAR_ITEMS[it].route }

    private fun screenOf(route: Any): NavDestination = checkNotNull(graph.findNode(route))

    // The app's screens whose routes take no arguments, registered the way TweeterApp's NavHost
    // registers them.
    private fun appGraph(): NavGraph {
        val provider = NavigatorProvider()
        provider.addNavigator(NavGraphNavigator(provider))
        provider.addNavigator(ComposeNavigator())
        return NavGraphBuilder(provider, FeedRoute::class, null, emptyMap()).apply {
            composable<FeedRoute> {}
            composable<SearchRoute> {}
            composable<PostRoute> {}
            composable<MapRoute> {}
            composable<ProfileRoute> {}
            composable<LogInRoute> {}
            composable<CreateAccountRoute> {}
            composable<EditProfileRoute> {}
        }.build()
    }
}
