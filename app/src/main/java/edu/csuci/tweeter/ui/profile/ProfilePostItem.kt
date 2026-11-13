package edu.csuci.tweeter.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.ui.feed.PhotoCard

/**
 * One of the user's posts on their profile: the photo as Feed shows it, the caption, and where
 * and how long ago it was shared. It takes the values it shows rather than the post's row, so
 * it's skipped when Profile recomposes with the same values.
 *
 * @param imageUri The post's photo, as [PhotoCard] takes it.
 * @param caption The post's caption; blank when it has none.
 * @param details Where the post was shared and how long ago, as
 *     [edu.csuci.tweeter.ui.postDetailsText] puts it.
 * @param modifier Applied to the whole item.
 */
@Composable
fun ProfilePostItem(
    imageUri: String,
    caption: String,
    details: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PhotoCard(imageUri = imageUri)
        if (caption.isNotBlank()) {
            Text(text = caption, style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            text = details,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
