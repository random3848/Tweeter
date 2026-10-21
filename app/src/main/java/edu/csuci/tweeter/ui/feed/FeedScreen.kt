package edu.csuci.tweeter.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R

/**
 * The photo-only home feed, the screen the app opens to.
 *
 * Displays photo posts from [FeedViewModel], sorted by timestamp, in a clean scrolling layout.
 *
 * @param modifier Applied to the whole screen.
 * @param feedViewModel Supplies photo posts and UI state for the feed.
 */
@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    feedViewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory)
) {
    val uiState by feedViewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.photoPosts.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.feed_empty_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = uiState.photoPosts,
                key = { post -> post.id }
            ) { post ->
                PhotoCard(imageUri = post.imageUri)
            }
        }
    }
}
