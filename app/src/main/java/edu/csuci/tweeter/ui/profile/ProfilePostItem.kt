package edu.csuci.tweeter.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.ui.feed.PhotoCard
import edu.csuci.tweeter.ui.feed.postDetailsText

/**
 * One of the user's posts on their profile: the photo as Feed shows it, the caption, and where
 * and how long ago it was shared.
 *
 * @param table The posts the item's post is in.
 * @param row The row of [table] holding the post to show.
 * @param modifier Applied to the whole item.
 */
@Composable
fun ProfilePostItem(table: PostTable, row: Int, modifier: Modifier = Modifier) {
    val caption = table.caption(row)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PhotoCard(imageUri = table.imageUri(row))
        if (caption.isNotBlank()) {
            Text(text = caption, style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            text = postDetailsText(table.locationName(row), table.timestampMillis(row)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
