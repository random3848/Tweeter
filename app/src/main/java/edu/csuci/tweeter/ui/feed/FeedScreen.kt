package edu.csuci.tweeter.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.ui.profile.timeAgoText

/**
 * The home feed, the screen the app opens to.
 *
 * Displays posts from [FeedViewModel], sorted by timestamp, in a clean scrolling layout. A label
 * on each post's photo shows the post on Map. When another screen asks the feed to show a post,
 * as Map's popup does, the feed scrolls to it.
 *
 * @param onShowOnMap Opens Map, once the post the user chose is requested there.
 * @param modifier Applied to the whole screen.
 * @param feedViewModel Supplies posts and UI state for the feed.
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

    LaunchedEffect(postToShow, uiState.posts) {
        val postId = postToShow ?: return@LaunchedEffect
        val index = uiState.posts.indexOfFirst { it.id == postId }
        // The feed can still show the posts it had before this one was shared, so the request
        // stays until a list with the post arrives.
        if (index >= 0) {
            listState.scrollToItem(index)
            feedViewModel.clearPostToShow()
        }
    }

    when {
        uiState.isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.posts.isEmpty() -> {
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
        }

        else -> {
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(
                    items = uiState.posts,
                    key = { _, post -> post.id }
                ) { index, post ->
                    FeedPostItem(
                        post = post,
                        onShowOnMap = {
                            feedViewModel.showOnMap(post)
                            onShowOnMap()
                        }
                    )
                    if (index < uiState.posts.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * A post item in the home feed displaying poster info, photo or placeholder, caption,
 * animal type, confidence, and location details. A label on the photo shows the post on Map.
 *
 * @param post The post to render.
 * @param onShowOnMap Shows the post on Map.
 * @param modifier Applied to the item container.
 */
@Composable
private fun FeedPostItem(post: Post, onShowOnMap: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = post.avatar.initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = post.username,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val timeAgo = timeAgoText(post.timestamp)
                val locationDetails = if (post.locationName.isNotBlank()) {
                    stringResource(R.string.post_details, post.locationName, timeAgo)
                } else {
                    timeAgo
                }
                Text(
                    text = locationDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box {
            PhotoCard(imageUri = post.imageUri)
            ShowOnMapLabel(
                onClick = onShowOnMap,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }

        val hasCaption = post.caption.isNotBlank()

        if (hasCaption) {
            Text(
                text = post.caption,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp)
            )
        }
        val hasAnimalType = post.animalType.isNotBlank()
        val hasConfidence = post.confidence != null

        if (hasAnimalType || hasConfidence) {
            val animalStr = if (hasAnimalType) post.animalType else null
            val confidenceStr = post.confidence?.let {
                stringResource(R.string.feed_confidence_rating, it)
            }
            val infoText = listOfNotNull(animalStr, confidenceStr).joinToString(" · ")

            Text(
                text = infoText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 8.dp)
            )
        }
    }
}

/**
 * A label over a post's photo that shows the post on Map when pressed. The place's name, if the
 * post has one, is already in the post's header.
 *
 * @param onClick Shows the post on Map.
 * @param modifier Applied to the label.
 */
@Composable
private fun ShowOnMapLabel(onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            text = showOnMap,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
