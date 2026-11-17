package edu.csuci.tweeter.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.CenteredMessage
import edu.csuci.tweeter.ui.navigation.TweeterTopBar

/**
 * The home feed, the screen the app opens to, under the app's top bar.
 *
 * Displays posts from [FeedViewModel], newest first, in a clean scrolling layout. A label
 * on each post's photo shows the post on Map. When another screen asks the feed to show a post,
 * as Map's popup does, the feed scrolls to it.
 *
 * @param onShowOnMap Opens Map, once the post the user chose is requested there.
 * @param modifier Applied to the whole screen.
 * @param topBarActions Buttons on the right of the top bar, such as the profile button.
 * @param feedViewModel Supplies posts and UI state for the feed.
 * @param onOpenProfile Opens the selected author's profile.
 */
@Composable
fun FeedScreen(
    onShowOnMap: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    topBarActions: @Composable RowScope.() -> Unit = {},
    feedViewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory)
) {
    val uiState by feedViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val rowToShow = uiState.rowToShow
    LaunchedEffect(rowToShow) {
        if (rowToShow != null) {
            // Feed lists every row in order, so a post's row is its place in the list.
            listState.scrollToItem(rowToShow)
            feedViewModel.clearPostToShow()
        }
    }

    // Takes the post's ID, so that it stays the same lambda as new tables arrive.
    val showPostOnMap = { postId: String ->
        feedViewModel.showOnMap(postId)
        onShowOnMap()
    }
    Column(modifier = modifier.fillMaxSize()) {
        TweeterTopBar(actions = topBarActions)
        val bodyModifier = Modifier.weight(1f).fillMaxWidth()
        when {
            uiState.isLoading -> {
                Box(modifier = bodyModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.posts.size == 0 -> {
                CenteredMessage(
                    text = stringResource(R.string.feed_empty_message),
                    modifier = bodyModifier
                )
            }

            else -> {
                PostList(
                    listedPosts = uiState.listedPosts,
                    onShowOnMap = showPostOnMap,
                    onOpenProfile = onOpenProfile,
                    modifier = bodyModifier,
                    listState = listState
                )
            }
        }
    }
}
