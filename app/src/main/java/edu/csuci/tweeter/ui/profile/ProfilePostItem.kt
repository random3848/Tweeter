package edu.csuci.tweeter.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.ui.feed.PhotoCard

/**
 * One of the user's posts on their profile: the photo as Feed shows it, the caption, and where
 * and how long ago it was shared.
 *
 * @param post The post to show.
 * @param modifier Applied to the whole item.
 */
@Composable
fun ProfilePostItem(post: Post, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PhotoCard(imageUri = post.imageUri)
        if (post.caption.isNotBlank()) {
            Text(text = post.caption, style = MaterialTheme.typography.bodyMedium)
        }

        val timeAgo = timeAgoText(post.timestamp)
        Text(
            text = if (post.locationName.isBlank()) {
                timeAgo
            } else {
                stringResource(R.string.profile_post_details, post.locationName, timeAgo)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
