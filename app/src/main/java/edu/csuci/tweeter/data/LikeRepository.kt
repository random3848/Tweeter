package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow

/**
 * Which posts the logged-in user liked, and liking and unliking posts, which also changes their
 * [Post.likeCount].
 *
 * Guests can only browse: they have liked no posts, and can't like any.
 */
interface LikeRepository {
    /**
     * The IDs of the posts the logged-in user liked, or none while nobody is logged in. Emits
     * again whenever the user likes or unlikes a post, or someone else logs in.
     */
    fun getLikedPostIds(): Flow<Set<String>>

    /**
     * Likes the post with [postId] for the logged-in user, adding one to its like count.
     *
     * @throws LikeException If the like can't be saved, such as when nobody is logged in.
     */
    suspend fun like(postId: String)

    /**
     * Takes back the logged-in user's like of the post with [postId], taking one from its like
     * count.
     *
     * @throws LikeException If the change can't be saved, such as when nobody is logged in.
     */
    suspend fun unlike(postId: String)
}
