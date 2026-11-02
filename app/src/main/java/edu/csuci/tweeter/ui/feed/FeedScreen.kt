package edu.csuci.tweeter.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R

/**
 * The photo-only home feed, the screen the app opens to.
 *
 * Displays photo posts from [FeedViewModel], sorted by timestamp, in a clean scrolling layout.
 * Each photo has a label saying where it was taken; pressing it shows the post on Map. When
 * another screen asks the feed to show a post, as Map's popup does, the feed scrolls to it.
 *
 * @param onShowOnMap Opens Map once the post the user chose is selected there.
 * @param modifier Applied to the whole screen.
 * @param feedViewModel Supplies photo posts and UI state for the feed.
 */
@Composable
fun FeedScreen(
    onShowOnMap: () -> Unit,
    modifier: Modifier = Modifier,
    feedViewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory)
) {
    val uiState by feedViewModel.uiState.collectAsStateWithLifecycle()
    val postToShow by feedViewModel.postToShow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(postToShow, uiState.photoPosts) {
        val postId = postToShow ?: return@LaunchedEffect
        val posts = uiState.photoPosts
        // The post came from another screen, so an empty feed hasn't loaded yet.
        if (posts.isEmpty()) {
            return@LaunchedEffect
        }
        val index = posts.indexOfFirst { it.id == postId }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
        feedViewModel.clearPostToShow()
    }

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
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = uiState.photoPosts,
                key = { post -> post.id }
            ) { post ->
                Box {
                    PhotoCard(imageUri = post.imageUri)
                    LocationLabel(
                        locationName = post.locationName,
                        onClick = {
                            feedViewModel.showOnMap(post)
                            onShowOnMap()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

/**
 * A label over a post's photo with the name of the place where it was taken, or "Show on map" if
 * the place has no name. Pressing it shows the post on Map.
 *
 * @param locationName The place's name, or blank if it has none.
 * @param onClick Shows the post on Map.
 * @param modifier Applied to the label.
 */
@Composable
private fun LocationLabel(
    locationName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showOnMap = stringResource(R.string.feed_show_on_map)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClickLabel = showOnMap, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_location_pin),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = locationName.ifBlank { showOnMap },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
