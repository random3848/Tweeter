package edu.csuci.tweeter.ui.feed

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import edu.csuci.tweeter.R

/**
 * The delete button on the logged-in user's own posts, in Feed and on Profile. Pressing it asks
 * before deleting, since a deleted post can't be brought back.
 *
 * @param postId The post's ID, which [onDelete] is called with. Taking the ID rather than a
 *     lambda per post keeps [onDelete] the same for every post, so unchanged posts are skipped.
 * @param onDelete Deletes the post with the given ID, once the user confirms.
 * @param modifier Applied to the button.
 */
@Composable
fun DeletePostButton(postId: String, onDelete: (String) -> Unit, modifier: Modifier = Modifier) {
    var isConfirming by rememberSaveable { mutableStateOf(false) }

    IconButton(onClick = { isConfirming = true }, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_delete),
            contentDescription = stringResource(R.string.feed_delete_post),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (isConfirming) {
        DeleteConfirmDialog(
            onConfirm = {
                isConfirming = false
                onDelete(postId)
            },
            onDismiss = { isConfirming = false }
        )
    }
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feed_delete_title)) },
        text = { Text(stringResource(R.string.feed_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.feed_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.feed_delete_cancel))
            }
        }
    )
}
