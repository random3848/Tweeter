package edu.csuci.tweeter.ui.saved

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.feed.PostBookmarks
import edu.csuci.tweeter.ui.feed.PostLikes
import edu.csuci.tweeter.ui.feed.PostList
import edu.csuci.tweeter.ui.navigation.BackTopBar

/**
 * The posts the logged-in user saved to view later, newest shared first, as Feed shows posts, under
 * a top bar with a back arrow. Profile's "Saved posts" button opens it. Pressing a post's filled
 * bookmark unsaves it, which takes it off the list. As in Feed, a heart under each post likes it,
 * and the logged-in user's own posts have a delete button.
 *
 * @param onBack Returns to the screen it was opened from.
 * @param onShowOnMap Opens Map, once the post the user chose is requested there.
 * @param onOpenProfile Opens the profile of the user with the given ID.
 * @param onLogIn Opens Log in, for a guest who tries to like or save a post or wants to comment.
 * @param modifier Applied to the whole screen.
 * @param viewModel Supplies the saved posts.
 */
@Composable
fun SavedPostsScreen(
    onBack: () -> Unit,
    onShowOnMap: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedPostsViewModel = viewModel(factory = SavedPostsViewModel.factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loggedInUid by viewModel.loggedInUid.collectAsStateWithLifecycle()
    // Takes the post's ID, so that it stays the same lambda as new tables arrive.
    val showPostOnMap = { postId: String ->
        viewModel.showOnMap(postId)
        onShowOnMap()
    }
    val likePost = if (uiState.isLoggedIn) viewModel::toggleLike else { _: String -> onLogIn() }
    val bookmarkPost =
        if (uiState.isLoggedIn) viewModel::toggleBookmark else { _: String -> onLogIn() }
    Column(modifier = modifier.fillMaxSize()) {
        BackTopBar(title = stringResource(R.string.saved_posts_title), onBack = onBack)
        PostList(
            listedPosts = uiState.listedPosts,
            isLoading = uiState.isLoading,
            emptyMessage = stringResource(R.string.saved_posts_empty),
            loggedInUid = loggedInUid,
            onShowOnMap = showPostOnMap,
            onDelete = viewModel::deletePost,
            onOpenProfile = onOpenProfile,
            onLogIn = onLogIn,
            likes = PostLikes(likedPostIds = uiState.likedPostIds, onLike = likePost),
            bookmarks = PostBookmarks(
                bookmarkedPostIds = uiState.bookmarkedPostIds,
                onBookmark = bookmarkPost
            ),
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}
