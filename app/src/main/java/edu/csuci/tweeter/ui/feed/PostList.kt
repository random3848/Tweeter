package edu.csuci.tweeter.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.PostRows
import edu.csuci.tweeter.ui.CenteredMessage
import edu.csuci.tweeter.ui.comments.PostComments
import edu.csuci.tweeter.ui.postDetailsText
import edu.csuci.tweeter.ui.report.ReportPostButton

// Between a post's animal type and its confidence rating.
private const val ANIMAL_DETAILS_SEPARATOR = " · "

/**
 * A screen's posts: a spinner while they are read, [emptyMessage] when there are none, and
 * otherwise the posts of [listedPosts] as a scrolling list, in the order of its rows, with a
 * divider between posts. Each has its author, photo, caption and animal, as Feed shows them, and
 * the logged-in user's own posts have a delete button, which asks before deleting. Each also has
 * a like button and its like count, a button that opens its comments under it, where logged-in
 * users can add one, and at the right end a bookmark that saves the post for the user to view
 * later. Other users' posts have a flag that reports them.
 *
 * @param listedPosts The posts to list.
 * @param isLoading Whether the posts are still being read.
 * @param emptyMessage What to say when there are no posts to list.
 * @param loggedInUid The logged-in user's ID, whose posts get a delete button, or null for guests.
 * @param onShowOnMap Shows the post with the given ID on Map. It takes the ID rather than
 *     capturing the table, so it stays the same as new tables arrive.
 * @param onDelete Deletes the post with the given ID. It takes the ID for the same reason.
 * @param onOpenProfile Opens the profile of the author with the given ID.
 * @param onLogIn Opens Log in, for a guest who wants to comment or report a post.
 * @param likes Which posts the user liked and what pressing a like button does. It's required, so
 *     no screen listing posts leaves out their likes.
 * @param bookmarks Which posts the user saved and what pressing a bookmark does. It's required
 *     for the same reason.
 * @param modifier Applied to the space the posts take.
 * @param listState The list's scroll position.
 */
@Composable
internal fun PostList(
    listedPosts: PostRows,
    isLoading: Boolean,
    emptyMessage: String,
    loggedInUid: String?,
    onShowOnMap: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onLogIn: () -> Unit,
    likes: PostLikes,
    bookmarks: PostBookmarks,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    when {
        isLoading -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        listedPosts.rows.isEmpty() -> CenteredMessage(text = emptyMessage, modifier = modifier)

        else -> {
            PostItems(
                listedPosts = listedPosts,
                loggedInUid = loggedInUid,
                onShowOnMap = onShowOnMap,
                onDelete = onDelete,
                onOpenProfile = onOpenProfile,
                onLogIn = onLogIn,
                listState = listState,
                likes = likes,
                bookmarks = bookmarks,
                modifier = modifier
            )
        }
    }
}

// This is where an item reads its post's row: the parts it's made of get the values they show, so
// a part whose values didn't change is skipped when a new table arrives.
@Composable
private fun PostItems(
    listedPosts: PostRows,
    loggedInUid: String?,
    onShowOnMap: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onLogIn: () -> Unit,
    listState: LazyListState,
    likes: PostLikes,
    bookmarks: PostBookmarks,
    modifier: Modifier = Modifier
) {
    val posts = listedPosts.table
    val rows = listedPosts.rows
    val lastIndex = rows.size - 1
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(count = rows.size, key = { index -> posts.id(rows[index]) }) { index ->
            val row = rows[index]
            val postId = posts.id(row)
            // Saved by post, so a post's comments stay open as the list scrolls and rotates.
            var showsComments by rememberSaveable(postId) { mutableStateOf(false) }
            PostHeader(
                postId = postId,
                avatarInitials = posts.avatarInitials(row),
                username = posts.username(row),
                details = postDetailsText(posts.locationName(row), posts.timestampMillis(row)),
                authorUid = posts.authorUid(row),
                // A guest's ID is null, and a post shared while logged out has no author, so
                // neither matches.
                isMine = loggedInUid != null && posts.authorUid(row) == loggedInUid,
                onOpenProfile = onOpenProfile,
                onDelete = onDelete
            )
            PhotoWithMapLabel(
                postId = postId,
                imageUri = posts.imageUri(row),
                onShowOnMap = onShowOnMap
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LikeButton(
                    likeCount = posts.likeCount(row),
                    isLiked = postId in likes.likedPostIds,
                    onClick = { likes.onLike(postId) }
                )
                CommentsButton(
                    isOpen = showsComments,
                    onClick = { showsComments = !showsComments }
                )
                // Other users' posts only; one shared while logged out has nobody to report.
                val authorUid = posts.authorUid(row)
                if (authorUid.isNotBlank() && authorUid != loggedInUid) {
                    ReportPostButton(
                        postId = postId,
                        authorUid = authorUid,
                        isLoggedIn = loggedInUid != null,
                        onLogIn = onLogIn,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // Takes the leftover width, which puts the bookmark at the right end.
                Spacer(modifier = Modifier.weight(1f))
                BookmarkButton(
                    isBookmarked = postId in bookmarks.bookmarkedPostIds,
                    onClick = { bookmarks.onBookmark(postId) }
                )
            }
            PostCaption(caption = posts.caption(row))
            AnimalDetails(text = animalDetailsOf(posts.animalType(row), posts.confidence(row)))
            if (showsComments) {
                PostComments(postId = postId, onLogIn = onLogIn)
            }
            if (index < lastIndex) {
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
 * The top of a post: the poster's avatar and name, where the post was shared and how long ago.
 * Pressing it opens the author's profile. On the logged-in user's own posts it also has a delete
 * button, which asks before deleting. It receives the displayed texts and author UID separately
 * from the post table.
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
 * A post's photo, with a label over it that shows the post on Map.
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
 * A heart that likes a post, filled once the user liked it, and how many users did.
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
 * A post's caption, or nothing if it is blank.
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
 * A post's animal type and confidence rating, or nothing if it has neither.
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
