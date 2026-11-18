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
 * The button under a post's photo that opens its comments under the post, and closes them again.
 * Guests can open them too, to read.
 *
 * @param isOpen Whether the post's comments are open, which colors the button.
 * @param onClick Opens the comments, or closes them when they're open.
 */
@Composable
fun CommentsButton(isOpen: Boolean, onClick: () -> Unit) {
    IconToggleButton(
        checked = isOpen,
        onCheckedChange = { onClick() },
        // Level with the like button beside it, which is padded the same.
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_comment),
            contentDescription = stringResource(R.string.feed_open_comments),
            tint = if (isOpen) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
