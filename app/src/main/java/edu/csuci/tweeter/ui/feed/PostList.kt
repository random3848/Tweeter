package edu.csuci.tweeter.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import edu.csuci.tweeter.data.countedVoteOn
import edu.csuci.tweeter.ui.CenteredMessage
import edu.csuci.tweeter.ui.comments.PostComments
import edu.csuci.tweeter.ui.postDetailsText
import edu.csuci.tweeter.ui.report.ReportPostButton
import edu.csuci.tweeter.ui.voting.AnimalVoteControl
import edu.csuci.tweeter.ui.voting.PostVotes
import edu.csuci.tweeter.ui.voting.Voter

/**
 * A screen's posts: a spinner while they are read, [emptyMessage] when there are none, and
 * otherwise the posts of [listedPosts] as a scrolling list, in the order of its rows, with a
 * divider between posts. Each has its author, photo, caption and the poster's confidence, as Feed
 * shows them, and the logged-in user's own posts have a delete button, which asks before deleting.
 * Each also has a like button and its like count, a button that opens its comments under it,
 * where logged-in users can add one, its animal name control ([AnimalVoteControl]), which asks
 * what animal it is when the poster didn't know, and at the right end a bookmark that saves the
 * post for the user to view later. Other users' posts have a flag that reports them.
 *
 * @param listedPosts The posts to list.
 * @param isLoading Whether the posts are still being read.
 * @param emptyMessage What to say when there are no posts to list.
 * @param loggedInUid The logged-in user's ID, whose posts get a delete button, or null for guests.
 * @param onShowOnMap Shows the post with the given ID on Map. It takes the ID rather than
 *     capturing the table, so it stays the same as new tables arrive.
 * @param onDelete Deletes the post with the given ID. It takes the ID for the same reason.
 * @param onOpenProfile Opens the profile of the author with the given ID.
 * @param onLogIn Opens Log in, for a guest who wants to comment, vote or report a post.
 * @param likes Which posts the user liked and what pressing a like button does. It's required, so
 *     no screen listing posts leaves out their likes.
 * @param votes The user's votes on what animal posts show, and what the animal name controls do.
 *     It's required for the same reason.
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
    votes: PostVotes,
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
                votes = votes,
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
    votes: PostVotes,
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
            val authorUid = posts.authorUid(row)
            PostHeader(
                avatarInitials = posts.avatarInitials(row),
                username = posts.username(row),
                details = postDetailsText(posts.locationName(row), posts.timestampMillis(row)),
                authorUid = authorUid,
                onOpenProfile = onOpenProfile
            ) {
                PostHeaderAction(
                    postId = postId,
                    authorUid = authorUid,
                    loggedInUid = loggedInUid,
                    onDelete = onDelete,
                    onLogIn = onLogIn
                )
            }
            PhotoWithMapLabel(
                postId = postId,
                imageUri = posts.imageUri(row),
                onShowOnMap = onShowOnMap
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Takes the width the bookmark leaves, which puts the bookmark at the right end.
                Row(
                    modifier = Modifier.weight(1f),
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
                    val round = posts.voteRound(row)
                    AnimalVoteControl(
                        postId = postId,
                        posterAnimal = posts.animalType(row),
                        shownAnimal = posts.shownAnimal(row),
                        agreement = posts.agreement(row),
                        round = round,
                        voter = Voter.of(loggedInUid, authorUid),
                        userVote = votes.userVotes.countedVoteOn(postId, round),
                        actions = votes.actions,
                        onLogIn = onLogIn,
                        // Takes only the width it needs, and cuts a long name short rather than
                        // pushing the bookmark off the row.
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(start = 4.dp, top = 4.dp)
                    )
                }
                BookmarkButton(
                    isBookmarked = postId in bookmarks.bookmarkedPostIds,
                    onClick = { bookmarks.onBookmark(postId) }
                )
            }
            PostCaption(caption = posts.caption(row))
            ConfidenceRating(confidence = posts.confidence(row))
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
 * The top of a post: the poster's avatar and name, where the post was shared and how long ago,
 * and [action] at the right end. Pressing it opens the author's profile. It receives the displayed
 * texts and author UID separately from the post table.
 *
 * @param avatarInitials The initials in the poster's avatar.
 * @param username The poster's name.
 * @param details Where the post was shared and how long ago, as [postDetailsText] puts it.
 * @param authorUid The Firebase UID of the post's author.
 * @param onOpenProfile Opens the profile of the author with the given UID.
 * @param action The button at the right end, as [PostHeaderAction] chooses it.
 */
@Composable
private fun PostHeader(
    avatarInitials: String,
    username: String,
    details: String,
    authorUid: String,
    onOpenProfile: (String) -> Unit,
    action: @Composable () -> Unit
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

        // Takes the leftover width, which puts the action at the right edge.
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

        action()
    }
}

/**
 * The button at the right end of a post's header: on the logged-in user's own posts a delete
 * button, which asks before deleting, and on other users' posts a flag that reports them. It's in
 * the header rather than the like and comments row so the animal's name has room there.
 *
 * @param postId The post's ID.
 * @param authorUid The Firebase UID of the post's author; empty for a post shared while logged
 *     out, which has nobody to report.
 * @param loggedInUid The logged-in user's ID, or null for guests, whose flag opens Log in.
 * @param onDelete Deletes the post with the given ID, once the user confirms.
 * @param onLogIn Opens Log in, for a guest who wants to report the post.
 */
@Composable
private fun PostHeaderAction(
    postId: String,
    authorUid: String,
    loggedInUid: String?,
    onDelete: (String) -> Unit,
    onLogIn: () -> Unit
) {
    val isMine = loggedInUid != null && authorUid == loggedInUid
    if (isMine) {
        DeletePostButton(postId = postId, onDelete = onDelete)
    } else if (authorUid.isNotBlank()) {
        ReportPostButton(
            postId = postId,
            authorUid = authorUid,
            isLoggedIn = loggedInUid != null,
            onLogIn = onLogIn
        )
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
 * The poster's confidence in the animal they named, or nothing if they gave none.
 *
 * @param confidence The poster's confidence, from 1 to 5, or null when unspecified.
 */
@Composable
private fun ConfidenceRating(confidence: Int?) {
    if (confidence != null) {
        Text(
            text = stringResource(R.string.feed_confidence_rating, confidence),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 8.dp)
        )
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
