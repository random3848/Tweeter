package edu.csuci.tweeter.ui.feed

import android.util.Log
import edu.csuci.tweeter.data.LikeException
import edu.csuci.tweeter.data.LikeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

private const val TAG = "PostLiker"

/**
 * Likes posts and takes likes back for a screen that lists them with [PostList], such as Feed,
 * each screen holding its own.
 *
 * A post's heart ignores taps while its last change is being saved: a screen reads whether the
 * post is liked from [likedPostIds], which shows the change only once Firestore's listener
 * reports it, so a second tap before then would repeat the first.
 *
 * @param likeRepository Which posts the user liked, and where likes are saved.
 */
class PostLiker(private val likeRepository: LikeRepository) {
    // The IDs of the posts whose like change the server hasn't saved yet.
    private val postsWithLikeChanging = MutableStateFlow(emptySet<String>())

    /** The IDs of the posts the user liked, updated as they like posts or log in or out. */
    fun likedPostIds(): Flow<Set<String>> = likeRepository.getLikedPostIds()

    /**
     * Likes the post with [postId], or takes the like back if [isLiked], unless a change to its
     * like is still being saved. A change that can't be saved is logged; the heart then shows the
     * like as it was.
     *
     * @param isLiked Whether the user liked the post when they tapped its heart.
     */
    suspend fun toggleLike(postId: String, isLiked: Boolean) {
        val isAlreadyChanging = postId in postsWithLikeChanging.getAndUpdate { it + postId }
        if (isAlreadyChanging) {
            return
        }
        val change = if (isLiked) likeRepository::unlike else likeRepository::like
        saveLikeChange(postId, change)
    }

    // Saves [change] to the like of the post with [postId], then lets its heart take taps again.
    private suspend fun saveLikeChange(postId: String, change: suspend (postId: String) -> Unit) {
        try {
            change(postId)
        } catch (error: LikeException) {
            Log.e(TAG, "Could not change the like of post $postId", error)
        } finally {
            postsWithLikeChanging.update { it - postId }
        }
    }
}
