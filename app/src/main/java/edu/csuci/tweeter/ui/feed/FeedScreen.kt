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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.navigation.TweeterTopBar
import edu.csuci.tweeter.ui.postDetailsText

// Between a post's animal type and its confidence rating.
private const val ANIMAL_DETAILS_SEPARATOR = " · "

/**
 * The home feed, the screen the app opens to, under the app's top bar.
 *
 * Displays posts from [FeedViewModel], newest first, in a clean scrolling layout. A label
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
    val listState = rememberLazyListState()

    val rowToShow = uiState.rowToShow
    LaunchedEffect(rowToShow) {
        if (rowToShow != null) {
            listState.scrollToItem(rowToShow)
            feedViewModel.clearPostToShow()
        }
    }

    // Takes the post's ID, so that it stays the same lambda as new tables arrive.
    val showPostOnMap = { postId: String ->
        feedViewModel.showOnMap(postId)
        onShowOnMap()
    }
    val likePost = if (uiState.canLike) feedViewModel::toggleLike else { _: String -> onLogIn() }
    val controls = PostControls(
        onShowOnMap = showPostOnMap,
        onLike = likePost,
        onDelete = feedViewModel::deletePost,
        onOpenProfile = onOpenProfile
    )
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
                FeedList(
                    uiState = uiState,
                    listState = listState,
                    loggedInUid = loggedInUid,
                    controls = controls,
                    modifier = bodyModifier
                )
            }
        }
    }
}

/**
 * What pressing a feed post's controls does, each called with the post's ID, or its author's for
 * [onOpenProfile]. They take the ID rather than capturing the table, so they stay the same as new
 * tables arrive.
 *
 * @property onShowOnMap Shows the post on Map.
 * @property onLike Likes the post or takes the like back, or opens Log in for guests.
 * @property onDelete Deletes the post, once the user confirms.
 * @property onOpenProfile Opens the profile of the author with the given UID.
 */
private data class PostControls(
    val onShowOnMap: (String) -> Unit,
    val onLike: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onOpenProfile: (String) -> Unit
)

/**
 * The posts of [uiState] as a scrolling list, newest first, with a divider between posts.
 *
 * This is where a feed item reads its post's row: the parts it's made of get the values they
 * show, so a part whose values didn't change is skipped when a new table arrives.
 *
 * @param uiState The posts to list, in table order, and which of them the user liked.
 * @param listState The list's scroll position.
 * @param loggedInUid The logged-in user's ID, whose posts get a delete button, or null for guests.
 * @param controls What pressing each post's controls does.
 * @param modifier Applied to the list.
 */
@Composable
private fun FeedList(
    uiState: FeedUiState,
    listState: LazyListState,
    loggedInUid: String?,
    controls: PostControls,
    modifier: Modifier = Modifier
) {
    val posts = uiState.posts
    val lastRow = posts.size - 1
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(count = posts.size, key = { row -> posts.id(row) }) { row ->
            PostHeader(
                postId = posts.id(row),
                avatarInitials = posts.avatarInitials(row),
                username = posts.username(row),
                details = postDetailsText(posts.locationName(row), posts.timestampMillis(row)),
                // A guest's ID is null, and a post shared while logged out has no author, so
                // neither matches.
                isMine = loggedInUid != null && posts.authorUid(row) == loggedInUid,
                onDelete = controls.onDelete,
                authorUid = posts.authorUid(row),
                onOpenProfile = controls.onOpenProfile
            )
            val postId = posts.id(row)
            PhotoWithMapLabel(
                postId = postId,
                imageUri = posts.imageUri(row),
                onShowOnMap = controls.onShowOnMap
            )
            LikeButton(
                likeCount = posts.likeCount(row),
                isLiked = postId in uiState.likedPostIds,
                onClick = { controls.onLike(postId) }
            )
            PostCaption(caption = posts.caption(row))
            AnimalDetails(text = animalDetailsOf(posts.animalType(row), posts.confidence(row)))
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

/**
 * The top of a feed post: the poster's avatar and name, where the post was shared and how long
 * ago. Pressing it opens the author's profile. On the logged-in user's own posts it also has a
 * delete button, which asks before deleting. It receives the displayed texts and author UID
 * separately from the post table.
 *
 * @param postId The post's ID, which [onDelete] is called with.
 * @param avatarInitials The initials in the poster's avatar.
 * @param username The poster's name.
 * @param details Where the post was shared and how long ago, as [postDetailsText] puts it.
 * @param authorUid The Firebase UID of the post's author.
 * @param isMine Whether the logged-in user wrote the post, which shows the delete button.
 * @param onOpenProfile Opens the profile of the author with the given UID.
 * @param onDelete Deletes the post with the given ID, once the user confirms.
 */
@Composable
private fun PostHeader(
    postId: String,
    avatarInitials: String,
    username: String,
    details: String,
    authorUid: String,
    isMine: Boolean,
    onOpenProfile: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = authorUid.isNotBlank(),
                onClickLabel = stringResource(R.string.feed_open_author_profile),
                role = Role.Button,
                onClick = { onOpenProfile(authorUid) }
            )
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

        // Takes the leftover width, which puts the delete button at the right edge.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
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

        if (isMine) {
            DeletePostButton(postId = postId, onDelete = onDelete)
        }
    }
}

/**
 * A feed post's photo, with a label over it that shows the post on Map.
 *
 * @param postId The post's ID, which [onShowOnMap] is called with.
 * @param imageUri The post's photo, as [PhotoCard] takes it.
 * @param onShowOnMap Shows the post with the given ID on Map.
 */
@Composable
private fun PhotoWithMapLabel(postId: String, imageUri: String, onShowOnMap: (String) -> Unit) {
    Box {
        PhotoCard(imageUri = imageUri)
        ShowOnMapLabel(
            onClick = { onShowOnMap(postId) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
    }
}

/**
 * A heart that likes a feed post, filled once the user liked it, and how many users did.
 *
 * @param likeCount How many users liked the post.
 * @param isLiked Whether the user liked the post.
 * @param onClick Likes the post or takes the like back, or opens Log in for guests.
 */
@Composable
private fun LikeButton(likeCount: Int, isLiked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconToggleButton(checked = isLiked, onCheckedChange = { onClick() }) {
            Icon(
                painter = painterResource(
                    if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                ),
                contentDescription = stringResource(R.string.feed_like),
                tint = if (isLiked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        Text(
            text = pluralStringResource(R.plurals.feed_like_count, likeCount, likeCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * A feed post's caption, or nothing if it is blank.
 *
 * @param caption The post's caption.
 */
@Composable
private fun PostCaption(caption: String) {
    if (caption.isNotBlank()) {
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp)
        )
    }
}

/**
 * A feed post's animal type and confidence rating, or nothing if it has neither.
 *
 * @param text The animal type and rating, as [animalDetailsOf] puts them, or null.
 */
@Composable
private fun AnimalDetails(text: String?) {
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 8.dp)
        )
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
