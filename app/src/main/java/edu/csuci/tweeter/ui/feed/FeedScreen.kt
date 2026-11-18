package edu.csuci.tweeter.ui.feed

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
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
 * Displays posts from [FeedViewModel] in a clean scrolling layout, as the user picks with the chips
 * above them: everyone's by Best, Newest or Top, or, for users who are logged in, their friends'.
 * Picking one shows its first post. A label
 * on each post's photo shows the post on Map, and a heart under it likes the post, or opens Log in
 * for guests, who can't. When another screen asks the feed to show a post, as Map's popup does,
 * the feed scrolls to it. The logged-in user's own posts have a delete button, which asks before
 * deleting.
 *
 * @param onShowOnMap Opens Map, once the post the user chose is requested there.
 * @param onOpenProfile Opens the selected author's profile.
 * @param onLogIn Opens Log in, for a guest who tries to like a post.
 * @param modifier Applied to the whole screen.
 * @param topBarActions Buttons on the right of the top bar, such as the profile button.
 * @param feedViewModel Supplies posts and UI state for the feed.
 */
@Composable
fun FeedScreen(
    onShowOnMap: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
    topBarActions: @Composable RowScope.() -> Unit = {},
    feedViewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory)
) {
    val uiState by feedViewModel.uiState.collectAsStateWithLifecycle()
    val loggedInUid by feedViewModel.loggedInUid.collectAsStateWithLifecycle()
    // A new list state for each choice, which starts at its first post. One list state kept across
    // choices would keep the post at the top in view, wherever the new choice puts it.
    val listState = rememberSaveable(uiState.choice, saver = LazyListState.Saver) {
        LazyListState()
    }

    val positionToShow = uiState.positionToShow
    LaunchedEffect(positionToShow) {
        if (positionToShow != null) {
            listState.scrollToItem(positionToShow)
            feedViewModel.clearPostToShow()
        }
    }

    // Takes the post's ID, so that it stays the same lambda as new tables arrive.
    val showPostOnMap = { postId: String ->
        feedViewModel.showOnMap(postId)
        onShowOnMap()
    }
    val likePost = if (uiState.canLike) feedViewModel::toggleLike else { _: String -> onLogIn() }
    Column(modifier = modifier.fillMaxSize()) {
        TweeterTopBar(actions = topBarActions)
        FeedChoiceChips(
            choices = uiState.offeredChoices,
            selected = uiState.choice,
            onSelect = feedViewModel::choose
        )
        val bodyModifier = Modifier.weight(1f).fillMaxWidth()
        when (val authors = uiState.authors) {
            FeedAuthors.UnknownFriends -> {
                CenteredMessage(
                    text = stringResource(R.string.feed_friends_unknown),
                    modifier = bodyModifier
                ) {
                    TextButton(onClick = feedViewModel::retryFriends) {
                        Text(stringResource(R.string.feed_friends_retry))
                    }
                }
            }

            FeedAuthors.Everyone, FeedAuthors.LoadingFriends, is FeedAuthors.Friends -> {
                PostList(
                    listedPosts = uiState.listedPosts,
                    isLoading = uiState.isLoading || authors == FeedAuthors.LoadingFriends,
                    emptyMessage = stringResource(emptyMessageOf(authors)),
                    loggedInUid = loggedInUid,
                    onShowOnMap = showPostOnMap,
                    onDelete = feedViewModel::deletePost,
                    onOpenProfile = onOpenProfile,
                    modifier = bodyModifier,
                    listState = listState,
                    likes = PostLikes(likedPostIds = uiState.likedPostIds, onLike = likePost)
                )
            }
        }
    }
}

// What the feed says when it shows no posts: that nobody, or none of the user's friends, posted.
@StringRes
private fun emptyMessageOf(authors: FeedAuthors): Int =
    if (authors is FeedAuthors.Friends) R.string.feed_friends_empty else R.string.feed_empty_message
