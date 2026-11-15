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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomBarSelectionTest {
    private val graph: NavGraph = appGraph()

    @Test
    fun selectedTabIndex_noDestination_noTab() {
        assertNull(selectedTabIndex(BOTTOM_BAR_ITEMS, tabDestination = null))
    }

    @Test
    fun selectedTabIndex_feed_homeTab() {
        assertEquals(FeedRoute, selectedTabRouteOn(FeedRoute))
    }

    @Test
    fun selectedTabIndex_post_postTab() {
        assertEquals(PostRoute, selectedTabRouteOn(PostRoute))
    }

    @Test
    fun selectedTabIndex_map_mapTab() {
        assertEquals(MapRoute, selectedTabRouteOn(MapRoute))
    }

    @Test
    fun selectedTabIndex_profile_homeTab() {
        assertEquals(FeedRoute, selectedTabRouteOn(ProfileRoute))
    }

    @Test
    fun selectedTabIndex_logIn_noTab() {
        assertNull(selectedTabIndex(BOTTOM_BAR_ITEMS, destinationOf(LogInRoute)))
    }

    // The route of the tab selected while the screen of [route] is open, or null for none.
    private fun selectedTabRouteOn(route: Any): Any? =
        selectedTabIndex(BOTTOM_BAR_ITEMS, destinationOf(route))?.let { BOTTOM_BAR_ITEMS[it].route }

    private fun destinationOf(route: Any): NavDestination = checkNotNull(graph.findNode(route))

    // The app's screens, registered the way TweeterApp's NavHost registers them.
    private fun appGraph(): NavGraph {
        val provider = NavigatorProvider()
        provider.addNavigator(NavGraphNavigator(provider))
        provider.addNavigator(ComposeNavigator())
        return NavGraphBuilder(provider, FeedRoute::class, null, emptyMap()).apply {
            composable<FeedRoute> {}
            composable<PostRoute> {}
            composable<MapRoute> {}
            composable<ProfileRoute> {}
            composable<LogInRoute> {}
            composable<CreateAccountRoute> {}
            composable<EditProfileRoute> {}
        }.build()
    }
}
