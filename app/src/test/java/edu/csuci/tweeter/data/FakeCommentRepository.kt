package edu.csuci.tweeter.data

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A [CommentRepository] in memory for tests, holding every post's comments in the order they were
 * added. Its IDs are the comment's position in its post, so tests can predict them.
 */
class FakeCommentRepository : CommentRepository {
    private val commentsByPost = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())

    /** Whether adding a comment fails, as when the server rejects it. */
    var failsToAdd = false

    /** Whether reading comments fails, as when the rules refuse it. Set before reading. */
    var failsToRead = false

    override fun getComments(postId: String): Flow<List<Comment>> = if (failsToRead) {
        flow { throw CommentException(IllegalStateException("The server refused the read")) }
    } else {
        commentsByPost.map { it[postId].orEmpty() }
    }

    override suspend fun addComment(postId: String, author: UserProfile, text: String) {
        if (failsToAdd) {
            throw CommentException(IllegalStateException("The server rejected the comment"))
        }
        commentsByPost.update { byPost ->
            val comments = byPost[postId].orEmpty()
            val comment = Comment(
                id = comments.size.toString(),
                authorUid = author.uid,
                username = author.displayName,
                text = text,
                timestamp = Instant.EPOCH
            )
            byPost + (postId to comments + comment)
        }
    }
}
