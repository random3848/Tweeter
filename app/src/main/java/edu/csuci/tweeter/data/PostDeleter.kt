package edu.csuci.tweeter.data

import android.util.Log
import com.google.firebase.FirebaseException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "PostDeleter"

/**
 * Deletes the logged-in user's own posts, then their photos, for every screen that shows a delete
 * button: Feed and Profile.
 *
 * It works in [scope], which outlives the screens, so a photo is still deleted once the server has
 * deleted its post, even when the screen that asked has closed meanwhile, as long as the app runs.
 *
 * @param postRepository Where posts are deleted.
 * @param photoRepository Where a deleted post's photo is removed.
 * @param accountRepository Tells who is logged in, since only a post's author can delete it.
 * @param scope Where deletes run; the app's, so leaving a screen doesn't stop them.
 */
class PostDeleter(
    private val postRepository: PostRepository,
    private val photoRepository: PhotoRepository,
    private val accountRepository: AccountRepository,
    private val scope: CoroutineScope
) {
    /**
     * Starts deleting the post with [postId] in [posts], then its photo, and returns at once.
     * Ignored unless the logged-in user wrote it. Screens drop the post on their own, since they
     * follow the repository's posts. A delete the server refuses is logged, and keeps the photo.
     */
    fun deleteOwnPost(posts: PostTable, postId: String) {
        scope.launch {
            val row = posts.rowOf(postId) ?: return@launch
            if (isWrittenByLoggedInUser(posts.authorUid(row))) {
                deleteOrLogFailure(postId, photoUrl = posts.imageUri(row))
            }
        }
    }

    // A post shared by a guest has no author, which matches nobody.
    private fun isWrittenByLoggedInUser(authorUid: String): Boolean =
        authorUid.isNotEmpty() && authorUid == accountRepository.loggedInUid.value

    private suspend fun deleteOrLogFailure(postId: String, photoUrl: String) {
        try {
            deletePostThenPhoto(postId, photoUrl)
        } catch (error: FirebaseException) {
            Log.e(TAG, "Could not delete the post", error)
        }
    }

    // The photo goes only once the post is gone, so a post that stays never loses its photo.
    private suspend fun deletePostThenPhoto(postId: String, photoUrl: String) {
        postRepository.deletePost(postId)
        deleteIfUploaded(photoUrl)
    }

    // A post whose photo was never uploaded has none in Storage to delete.
    private suspend fun deleteIfUploaded(photoUrl: String) {
        if (photoUrl.startsWith("https://")) {
            photoRepository.deleteUnusedPhoto(photoUrl)
        }
    }
}
