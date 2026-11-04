package edu.csuci.tweeter.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.ui.navigation.TweeterTopBar

// Between a post's animal type and its confidence rating.
private const val ANIMAL_DETAILS_SEPARATOR = " · "

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
 */
@Composable
fun FeedScreen(
    onShowOnMap: () -> Unit,
    modifier: Modifier = Modifier,
    topBarActions: @Composable RowScope.() -> Unit = {},
    feedViewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory)
) {
    val uiState by feedViewModel.uiState.collectAsStateWithLifecycle()
    val postToShow by feedViewModel.postToShow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(postToShow, uiState.posts) {
        val postId = postToShow ?: return@LaunchedEffect
        // The feed can still show the posts it had before this one was shared, so the request
        // stays until a table with the post arrives.
        val row = uiState.posts.rowOf(postId) ?: return@LaunchedEffect
        listState.scrollToItem(row)
        feedViewModel.clearPostToShow()
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
                Box(modifier = bodyModifier, contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.feed_empty_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                val posts = uiState.posts
                val lastRow = posts.size - 1
                LazyColumn(
                    state = listState,
                    modifier = bodyModifier,
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(count = posts.size, key = { row -> posts.id(row) }) { row ->
                        FeedPostItem(
                            posts = posts,
                            row = row,
                            onShowOnMap = {
                                feedViewModel.showOnMap(posts.id(row))
                                onShowOnMap()
                            }
                        )
                        if (row < lastRow) {
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
}

/**
 * A post item in the home feed displaying poster info, photo or placeholder, caption,
 * animal type, confidence, and location details. A label on the photo shows the post on Map.
 *
 * This is where a feed item reads its post's row; the parts it's made of get the values they show.
 *
 * @param posts The table holding the post.
 * @param row The post's row in [posts].
 * @param onShowOnMap Shows the post on Map.
 * @param modifier Applied to the item container.
 */
@Composable
private fun FeedPostItem(
    posts: PostTable,
    row: Int,
    onShowOnMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PostHeader(
            avatarInitials = posts.avatarInitials(row),
            username = posts.username(row),
            details = postDetailsText(posts.locationName(row), posts.timestampMillis(row))
        )

        Box {
            PhotoCard(imageUri = posts.imageUri(row))
            ShowOnMapLabel(
                onClick = onShowOnMap,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }

        val caption = posts.caption(row)
        if (caption.isNotBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp)
            )
        }

        val animalDetails = animalDetailsOf(posts.animalType(row), posts.confidence(row))
        if (animalDetails != null) {
            Text(
                text = animalDetails,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 8.dp)
            )
        }
    }
}

/**
 * The top of a feed post: the poster's avatar and name, where the post was shared and how long
 * ago. It takes the three texts it shows rather than the post's row, so it's skipped when the feed
 * recomposes with the same texts.
 *
 * @param avatarInitials The initials in the poster's avatar.
 * @param username The poster's name.
 * @param details Where the post was shared and how long ago, as [postDetailsText] puts it.
 */
@Composable
private fun PostHeader(avatarInitials: String, username: String, details: String) {
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
                text = avatarInitials,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = username,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The animal type and confidence rating of a post, joined by [ANIMAL_DETAILS_SEPARATOR] when it
 * has both, or null when it has neither.
 *
 * @param animalType The animal the poster identified; blank when unspecified.
 * @param confidence The poster's confidence in [animalType], from 1 to 5, or null when unspecified.
 */
@Composable
private fun animalDetailsOf(animalType: String, confidence: Int?): String? {
    val confidenceRating = confidence?.let {
        stringResource(R.string.feed_confidence_rating, it)
    }
    return when {
        animalType.isBlank() -> confidenceRating
        confidenceRating == null -> animalType
        else -> animalType + ANIMAL_DETAILS_SEPARATOR + confidenceRating
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
