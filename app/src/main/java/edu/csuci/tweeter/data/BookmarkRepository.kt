package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow

/**
 * Which posts the logged-in user saved to view later ([Post.isBookmarked]), and saving and
 * unsaving posts. Each user's saved posts are their own: nobody else sees them, and saving a post
 * doesn't change it.
 *
 * Guests can only browse: they have saved no posts, and can't save any.
 */
interface BookmarkRepository {
    /**
     * The IDs of the posts the logged-in user saved, or none while nobody is logged in. Emits
     * again whenever the user saves or unsaves a post, or someone else logs in.
     */
    fun getBookmarkedPostIds(): Flow<Set<String>>

    /**
     * Saves the post with [postId] for the logged-in user.
     *
     * @throws BookmarkException If the bookmark can't be saved, such as when nobody is logged in.
     */
    suspend fun bookmark(postId: String)

    /**
     * Takes the post with [postId] out of the logged-in user's saved posts.
     *
     * @throws BookmarkException If the change can't be saved, such as when nobody is logged in.
     */
    suspend fun unbookmark(postId: String)
}
