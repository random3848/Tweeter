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
import edu.csuci.tweeter.ui.feed.PostList
import edu.csuci.tweeter.ui.navigation.BackTopBar

/**
 * The posts that name one animal, newest first, as Feed shows posts, under a top bar with the
 * animal's name. Search opens it. As in Feed, the logged-in user's own posts have a delete button.
 *
 * @param animal The animal's name, as posts name it.
 * @param onBack Returns to the screen it was opened from.
 * @param onShowOnMap Opens Map, once the post the user chose is requested there.
 * @param onOpenProfile Opens the profile of the user with the given ID.
 * @param modifier Applied to the whole screen.
 * @param viewModel Supplies the animal's posts.
 */
@Composable
fun AnimalPostsScreen(
    animal: String,
    onBack: () -> Unit,
    onShowOnMap: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnimalPostsViewModel = viewModel(
        factory = AnimalPostsViewModel.createFactory(animal)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loggedInUid by viewModel.loggedInUid.collectAsStateWithLifecycle()
    // Takes the post's ID, so that it stays the same lambda as new tables arrive.
    val showPostOnMap = { postId: String ->
        viewModel.showOnMap(postId)
        onShowOnMap()
    }
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
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}
