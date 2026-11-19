package edu.csuci.tweeter.ui.feed

import android.util.Log
import edu.csuci.tweeter.data.LikeException
import edu.csuci.tweeter.data.LikeRepository
import kotlinx.coroutines.flow.Flow

private const val TAG = "PostLiker"

/**
 * Likes posts and takes likes back for a screen that lists them with [PostList], such as Feed,
 * each screen holding its own.
 *
 * A post's heart ignores taps while its last change is being saved, as [PendingPostChanges]
 * explains.
 *
 * @param likeRepository Which posts the user liked, and where likes are saved.
 */
class PostLiker(private val likeRepository: LikeRepository) {
    private val pendingChanges = PendingPostChanges()

    /** The IDs of the posts the user liked, updated as they like posts or log in or out. */
    fun likedPostIds(): Flow<Set<String>> = likeRepository.getLikedPostIds()

    /**
     * Likes the post with [postId], unless a change to its like is still being saved. A like that
     * can't be saved is logged; the heart then shows the post unliked.
     */
    suspend fun like(postId: String) {
        changeLike(postId, likeRepository::like)
    }

    /**
     * Takes back the user's like of the post with [postId], unless a change to its like is still
     * being saved. A change that can't be saved is logged; the heart then shows the post liked.
     */
    suspend fun unlike(postId: String) {
        changeLike(postId, likeRepository::unlike)
    }

    // Saves [change] to the like of the post with [postId], unless another is still being saved.
    private suspend fun changeLike(postId: String, change: suspend (postId: String) -> Unit) {
        pendingChanges.saveUnlessPending(postId) { saveLikeChange(postId, change) }
    }

    // Saves [change] to the like of the post with [postId], logging a change that can't be saved.
    private suspend fun saveLikeChange(postId: String, change: suspend (postId: String) -> Unit) {
        try {
            change(postId)
        } catch (error: LikeException) {
            Log.e(TAG, "Could not change the like of post $postId", error)
        }
    }
}
