package edu.csuci.tweeter.ui.animal

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
import edu.csuci.tweeter.ui.feed.PostLikes
import edu.csuci.tweeter.ui.feed.PostList
import edu.csuci.tweeter.ui.navigation.BackTopBar
import edu.csuci.tweeter.ui.voting.PostVotes

/**
 * The posts that name one animal, newest first, as Feed shows posts, under a top bar with the
 * animal's name. Search opens it. As in Feed, a heart under each post likes it, or opens Log in for
 * guests, who can't, its animal name control votes on its animal, and the logged-in user's own
 * posts have a delete button.
 *
 * @param animal The animal's name, as posts name it.
 * @param onBack Returns to the screen it was opened from.
 * @param onShowOnMap Opens Map, once the post the user chose is requested there.
 * @param onOpenProfile Opens the profile of the user with the given ID.
 * @param onLogIn Opens Log in, for a guest who tries to like a post, vote, or comment.
 * @param onOpenAnimal Opens the page of the animal with the given name.
 * @param onOpenVoting Opens the voting page of the post with the given ID.
 * @param modifier Applied to the whole screen.
 * @param viewModel Supplies the animal's posts.
 */
@Composable
fun AnimalPostsScreen(
    animal: String,
    onBack: () -> Unit,
    onShowOnMap: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onLogIn: () -> Unit,
    onOpenAnimal: (String) -> Unit,
    onOpenVoting: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnimalPostsViewModel = viewModel(
        factory = AnimalPostsViewModel.createFactory(animal)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loggedInUid by viewModel.loggedInUid.collectAsStateWithLifecycle()
    val userVotes by viewModel.userVotes.collectAsStateWithLifecycle()
    // Takes the post's ID, so that it stays the same lambda as new tables arrive.
    val showPostOnMap = { postId: String ->
        viewModel.showOnMap(postId)
        onShowOnMap()
    }
    val likePost = if (uiState.canLike) viewModel::toggleLike else { _: String -> onLogIn() }
    Column(modifier = modifier.fillMaxSize()) {
        BackTopBar(title = animal, onBack = onBack)
        PostList(
            listedPosts = uiState.listedPosts,
            isLoading = uiState.isLoading,
            // None are left when every post naming the animal was deleted since Search found it.
            emptyMessage = stringResource(R.string.animal_posts_empty),
            loggedInUid = loggedInUid,
            onShowOnMap = showPostOnMap,
            onDelete = viewModel::deletePost,
            onOpenProfile = onOpenProfile,
            onLogIn = onLogIn,
            likes = PostLikes(likedPostIds = uiState.likedPostIds, onLike = likePost),
            votes = PostVotes(
                userVotes = userVotes,
                onChangeVote = viewModel::changeVote,
                onReclassify = viewModel::reclassify,
                onOpenAnimal = onOpenAnimal,
                onOpenVoting = onOpenVoting
            ),
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}
