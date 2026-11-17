package edu.csuci.tweeter.ui.animal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.feed.PostList
import edu.csuci.tweeter.ui.navigation.BackTopBar

/**
 * The posts that name one animal, newest first, as Feed shows posts, under a top bar with the
 * animal's name. Search opens it.
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
    // Takes the post's ID, so that it stays the same lambda as new tables arrive.
    val showPostOnMap = { postId: String ->
        viewModel.showOnMap(postId)
        onShowOnMap()
    }
    Column(modifier = modifier.fillMaxSize()) {
        BackTopBar(title = animal, onBack = onBack)
        val bodyModifier = Modifier.weight(1f).fillMaxWidth()
        when {
            uiState.isLoading -> {
                Box(modifier = bodyModifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.listedPosts.rows.isEmpty() -> NoPostsMessage(modifier = bodyModifier)

            else -> {
                PostList(
                    listedPosts = uiState.listedPosts,
                    onShowOnMap = showPostOnMap,
                    onOpenProfile = onOpenProfile,
                    modifier = bodyModifier
                )
            }
        }
    }
}

// What the screen says when every post naming the animal was deleted or renamed since Search.
@Composable
private fun NoPostsMessage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.animal_posts_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
