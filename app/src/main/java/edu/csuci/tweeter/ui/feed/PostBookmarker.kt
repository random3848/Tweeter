package edu.csuci.tweeter.ui.feed

import android.util.Log
import edu.csuci.tweeter.data.BookmarkException
import edu.csuci.tweeter.data.BookmarkRepository
import kotlinx.coroutines.flow.Flow

private const val TAG = "PostBookmarker"

/**
 * Saves posts for the user to view later and unsaves them, for a screen that lists them with
 * [PostList], such as Feed, each screen holding its own.
 *
 * A post's bookmark ignores taps while its last change is being saved, as [PendingPostChanges]
 * explains.
 *
 * @param bookmarkRepository Which posts the user saved, and where bookmarks are saved.
 */
class PostBookmarker(private val bookmarkRepository: BookmarkRepository) {
    private val pendingChanges = PendingPostChanges()

    /** The IDs of the posts the user saved, updated as they save posts or log in or out. */
    fun bookmarkedPostIds(): Flow<Set<String>> = bookmarkRepository.getBookmarkedPostIds()

    /**
     * Saves the post with [postId], unless a change to its bookmark is still being saved. A
     * bookmark that can't be saved is logged; the bookmark then shows the post unsaved.
     */
    suspend fun bookmark(postId: String) {
        changeBookmark(postId, bookmarkRepository::bookmark)
    }

    /**
     * Unsaves the post with [postId], unless a change to its bookmark is still being saved. A
     * change that can't be saved is logged; the bookmark then shows the post saved.
     */
    suspend fun unbookmark(postId: String) {
        changeBookmark(postId, bookmarkRepository::unbookmark)
    }

    // Saves [change] to the bookmark of the post with [postId], unless another is still being
    // saved.
    private suspend fun changeBookmark(postId: String, change: suspend (postId: String) -> Unit) {
        pendingChanges.saveUnlessPending(postId) { saveBookmarkChange(postId, change) }
    }

    // Saves [change] to the bookmark of the post with [postId], logging a change that can't be
    // saved.
    private suspend fun saveBookmarkChange(
        postId: String,
        change: suspend (postId: String) -> Unit
    ) {
        try {
            change(postId)
        } catch (error: BookmarkException) {
            Log.e(TAG, "Could not change the bookmark of post $postId", error)
        }
    }
}
