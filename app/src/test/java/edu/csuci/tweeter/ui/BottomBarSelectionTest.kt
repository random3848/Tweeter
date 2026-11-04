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
import org.junit.Test

class BottomBarSelectionTest {
    private val graph: NavGraph = appGraph()

    @Test
    fun selectedTabIndex_noDestination_noTab() {
        val index = selectedTabIndex(BOTTOM_BAR_ITEMS, tabDestination = null)

        assertEquals(NO_SELECTED_TAB, index)
    }

    @Test
    fun selectedTabIndex_feed_homeTab() {
        val index = selectedTabIndex(BOTTOM_BAR_ITEMS, destinationOf(FeedRoute))

        assertEquals(FeedRoute, BOTTOM_BAR_ITEMS[index].route)
    }

    @Test
    fun selectedTabIndex_post_postTab() {
        val index = selectedTabIndex(BOTTOM_BAR_ITEMS, destinationOf(PostRoute))

        assertEquals(PostRoute, BOTTOM_BAR_ITEMS[index].route)
    }

    @Test
    fun selectedTabIndex_map_mapTab() {
        val index = selectedTabIndex(BOTTOM_BAR_ITEMS, destinationOf(MapRoute))

        assertEquals(MapRoute, BOTTOM_BAR_ITEMS[index].route)
    }

    @Test
    fun selectedTabIndex_profile_homeTab() {
        val index = selectedTabIndex(BOTTOM_BAR_ITEMS, destinationOf(ProfileRoute))

        assertEquals(FeedRoute, BOTTOM_BAR_ITEMS[index].route)
    }

    @Test
    fun selectedTabIndex_logIn_noTab() {
        val index = selectedTabIndex(BOTTOM_BAR_ITEMS, destinationOf(LogInRoute))

        assertEquals(NO_SELECTED_TAB, index)
    }

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
