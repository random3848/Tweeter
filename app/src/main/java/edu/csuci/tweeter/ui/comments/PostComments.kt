package edu.csuci.tweeter.ui.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.Comment
import edu.csuci.tweeter.ui.timeAgoText

/**
 * A post's comments, opened under the post, oldest first, like Instagram's. Logged-in users get a
 * bubble to add one; guests can only read them, with a button to log in instead.
 *
 * @param postId The post whose comments show.
 * @param onLogIn Opens Log in, for a guest who wants to comment.
 * @param modifier Applied to the comments.
 * @param commentsViewModel Supplies the comments and sends new ones. Keyed by post, so each open
 *     post has its own.
 */
@Composable
fun PostComments(
    postId: String,
    onLogIn: () -> Unit,
    modifier: Modifier = Modifier,
    commentsViewModel: CommentsViewModel = viewModel(
        key = postId,
        factory = CommentsViewModel.createFactory(postId)
    )
) {
    val uiState by commentsViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommentList(uiState = uiState)
        if (uiState.canComment) {
            CommentBubble(
                draft = commentsViewModel.draft,
                isSending = commentsViewModel.isSending,
                onDraftChange = commentsViewModel::changeDraft,
                onSend = commentsViewModel::sendComment
            )
        } else {
            TextButton(onClick = onLogIn) {
                Text(stringResource(R.string.comments_log_in))
            }
        }
    }
}

// The comments, a small spinner while they're read, or a note when there are none to show. A post
// has few comments, so they're laid out in the post itself rather than in a list of their own.
@Composable
private fun CommentList(uiState: CommentsUiState) {
    when {
        uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))

        uiState.hasFailed -> CommentsNote(text = stringResource(R.string.comments_failed))

        uiState.comments.isEmpty() -> CommentsNote(text = stringResource(R.string.comments_empty))

        else -> {
            for (comment in uiState.comments) {
                key(comment.id) {
                    CommentItem(comment = comment)
                }
            }
        }
    }
}

@Composable
private fun CommentsNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// The commenter's name in bold with their comment after it, and how long ago they wrote it.
@Composable
private fun CommentItem(comment: Comment) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(comment.username)
                }
                append(' ')
                append(comment.text)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = timeAgoText(comment.timestamp.toEpochMilli()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// A rounded bubble to type a comment in, with its Post button inside it.
@Composable
private fun CommentBubble(
    draft: String,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    OutlinedTextField(
        value = draft,
        onValueChange = onDraftChange,
        placeholder = { Text(stringResource(R.string.comments_hint)) },
        trailingIcon = {
            TextButton(onClick = onSend, enabled = draft.isNotBlank() && !isSending) {
                Text(stringResource(R.string.comments_send))
            }
        },
        maxLines = 4,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedBorderColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
