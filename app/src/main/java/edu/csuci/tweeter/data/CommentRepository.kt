package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow

/** Where comments on posts are stored and read from. */
interface CommentRepository {
    /** The comments on the post with [postId], oldest first. Fails with [CommentException]. */
    fun getComments(postId: String): Flow<List<Comment>>

    /** Stores [text] as [author]'s comment on the post with [postId]. */
    suspend fun addComment(postId: String, author: UserProfile, text: String)
}
