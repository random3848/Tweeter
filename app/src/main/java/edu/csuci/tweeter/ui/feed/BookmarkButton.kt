package edu.csuci.tweeter.ui.feed

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R

/**
 * The bookmark at the right end of the row under a post's photo, which saves the post for the user
 * to view later, filled once they saved it.
 *
 * @param isBookmarked Whether the user saved the post.
 * @param onClick Saves the post or unsaves it, or opens Log in for guests.
 */
@Composable
internal fun BookmarkButton(isBookmarked: Boolean, onClick: () -> Unit) {
    IconToggleButton(
        checked = isBookmarked,
        onCheckedChange = { onClick() },
        // Level with the like button at the other end of the row, which is padded the same.
        modifier = Modifier.padding(top = 4.dp, end = 4.dp)
    ) {
        Icon(
            painter = painterResource(
                if (isBookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border
            ),
            contentDescription = stringResource(R.string.feed_bookmark),
            tint = if (isBookmarked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
