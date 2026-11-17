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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.PostRows
import edu.csuci.tweeter.ui.postDetailsText

// Between a post's animal type and its confidence rating.
private const val ANIMAL_DETAILS_SEPARATOR = " · "

/**
 * The posts of [listedPosts] as a scrolling list, in the order of its rows, with a divider
 * between posts: each with its author, photo, caption and animal, as Feed shows them.
 *
 * This is where an item reads its post's row: the parts it's made of get the values they show, so
 * a part whose values didn't change is skipped when a new table arrives.
 *
 * @param listedPosts The posts to list.
 * @param onShowOnMap Shows the post with the given ID on Map. It takes the ID rather than
 *     capturing the table, so it stays the same as new tables arrive.
 * @param onOpenProfile Opens the profile of the author with the given ID.
 * @param modifier Applied to the list.
 * @param listState The list's scroll position.
 */
@Composable
internal fun PostList(
    listedPosts: PostRows,
    onShowOnMap: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
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
            PostHeader(
                avatarInitials = posts.avatarInitials(row),
                username = posts.username(row),
                details = postDetailsText(posts.locationName(row), posts.timestampMillis(row)),
                authorUid = posts.authorUid(row),
                onOpenProfile = onOpenProfile
            )
            PhotoWithMapLabel(
                postId = posts.id(row),
                imageUri = posts.imageUri(row),
                onShowOnMap = onShowOnMap
            )
            PostCaption(caption = posts.caption(row))
            AnimalDetails(text = animalDetailsOf(posts.animalType(row), posts.confidence(row)))
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
 * It receives the displayed texts and author UID separately from the post table.
 *
 * @param avatarInitials The initials in the poster's avatar.
 * @param username The poster's name.
 * @param details Where the post was shared and how long ago, as [postDetailsText] puts it.
 * @param authorUid The Firebase UID of the post's author.
 * @param onOpenProfile Opens the profile of the author with the given ID.
 */
@Composable
private fun PostHeader(
    avatarInitials: String,
    username: String,
    details: String,
    authorUid: String,
    onOpenProfile: (String) -> Unit
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
