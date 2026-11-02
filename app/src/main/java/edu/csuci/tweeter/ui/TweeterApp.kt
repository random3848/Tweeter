@file:Suppress("ktlint:standard:kdoc")

package edu.csuci.tweeter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.feed.FeedScreen
import edu.csuci.tweeter.ui.map.MapScreen
import edu.csuci.tweeter.ui.navigation.BottomBarItem
import edu.csuci.tweeter.ui.navigation.TweeterBottomBar
import edu.csuci.tweeter.ui.navigation.TweeterTopBar
import edu.csuci.tweeter.ui.post.PostScreen
import edu.csuci.tweeter.ui.profile.ProfileButton
import edu.csuci.tweeter.ui.profile.ProfileScreen

// The tab the app opens to, and that back leads to from the other tabs: Feed.
private const val START_TAB = 0

/**
 * Bottom-bar items in display order, each with the screen it opens.
 * The first one is the screen the app opens to.
 */
private val BOTTOM_BAR_ITEMS: List<BottomBarItem> = listOf(
    BottomBarItem(
        selectedIcon = R.drawable.ic_home_filled,
        unselectedIcon = R.drawable.ic_home_unfilled,
        description = R.string.navigation_home,
        screen = { FeedScreen() }
    ),
    BottomBarItem(
        selectedIcon = R.drawable.ic_upload_filled,
        unselectedIcon = R.drawable.ic_upload_unfilled,
        description = R.string.navigation_create_post,
        screen = { PostScreen() }
    ),
    BottomBarItem(
        selectedIcon = R.drawable.ic_map_filled,
        unselectedIcon = R.drawable.ic_map_unfilled,
        description = R.string.navigation_map,
        screen = { MapScreen() }
    )
)

/**
 * The whole app: the top bar, the bottom bar, and the screen of the selected tab between them.
 *
 * Feed is the screen the app opens to. To add a screen with its own tab:
 * 1. Add the screen in its own package, `ui/<feature>/`; `ui/feed/` is the example.
 * 2. Add its item, with the screen, to [BOTTOM_BAR_ITEMS].
 *
 * Do both in the same pull request, so no tab ever opens an empty screen. Screens should not edit
 * this file beyond that item; keep everything else in the feature's package.
 *
 * A tab's screen is composed the first time the tab opens. While another tab is open it stays
 * composed and laid out but isn't placed, so it isn't drawn and gets no touches, focus or
 * accessibility reads, yet keeps all its state. Switching tabs only changes which screen is
 * placed, so it takes one frame, and every screen comes back as it was left. Above all, the map
 * keeps its drawn surface, which would otherwise show black and load its tiles again each time
 * the Map tab opened. Back from another tab returns to Home.
 *
 * Profile is not a tab: Feed's profile button opens it in Feed's place, back closes it, and Home
 * stays selected while it is open. Like Feed, it stays as it was left while another tab is open,
 * so Home comes back to it. It draws its own top bar with a back arrow, so the shared one is
 * hidden there.
 *
 * @param modifier Applied to the whole app
 */
@Composable
fun TweeterApp(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(START_TAB) }
    // In the order they were first opened. A tab is composed once it is in here.
    var openedTabs by rememberSaveable { mutableStateOf(listOf(START_TAB)) }
    // Profile belongs to Feed's tab, so it stays open there while another tab is open.
    var isProfileOpen by rememberSaveable { mutableStateOf(false) }
    val isOnProfile = isProfileOpen && selectedTab == START_TAB
    val focusManager = LocalFocusManager.current

    fun selectTab(tab: Int) {
        if (tab == selectedTab) {
            return
        }
        // A screen that isn't placed can't take focus, but it would keep the focus it has, and
        // the keys sent to it.
        focusManager.clearFocus()
        selectedTab = tab
        if (tab !in openedTabs) {
            openedTabs = openedTabs + tab
        }
    }

    fun openProfile() {
        // Feed isn't placed while Profile is open; see selectTab.
        focusManager.clearFocus()
        isProfileOpen = true
    }

    BackHandler(enabled = selectedTab != START_TAB || isProfileOpen) {
        if (selectedTab != START_TAB) {
            selectTab(START_TAB)
        } else {
            isProfileOpen = false
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (!isOnProfile) {
                TweeterTopBar(
                    actions = {
                        if (selectedTab == START_TAB) {
                            ProfileButton(onClick = { openProfile() })
                        }
                    }
                )
            }
        },
        bottomBar = {
            TweeterBottomBar(
                items = BOTTOM_BAR_ITEMS,
                isSelected = { item -> item == BOTTOM_BAR_ITEMS[selectedTab] },
                onSelect = { item -> selectTab(BOTTOM_BAR_ITEMS.indexOf(item)) }
            )
        }
    ) { innerPadding ->
        Box(
            // Without the shared top bar, innerPadding already includes the status bar; consuming
            // it stops Profile's own top bar from padding below the status bar a second time.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            for (tab in openedTabs) {
                key(tab) {
                    Box(
                        modifier = Modifier
                            .placeIf(tab == selectedTab && !isOnProfile)
                            .fillMaxSize()
                    ) {
                        BOTTOM_BAR_ITEMS[tab].screen?.invoke()
                    }
                }
            }
            if (isProfileOpen) {
                ProfileScreen(
                    onBack = { isProfileOpen = false },
                    modifier = Modifier.placeIf(isOnProfile)
                )
            }
        }
    }
}

/**
 * Measures and lays out the element as usual, but places it only while [isPlaced] is true. An
 * element that isn't placed isn't drawn and gets no touches, focus or accessibility reads.
 */
private fun Modifier.placeIf(isPlaced: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (isPlaced) {
            placeable.place(0, 0)
        }
    }
}
