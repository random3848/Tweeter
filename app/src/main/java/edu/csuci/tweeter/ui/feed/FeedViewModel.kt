package edu.csuci.tweeter.ui.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.FirebaseException
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.PhotoDeletionException
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The UI state of the home feed screen.
 *
 * @property posts The posts to display, newest first; the feed shows the table's rows in order.
 * @property isLoading Whether the first posts are still being loaded from the repository.
 * @property rowToShow The row in [posts] of the post another screen asked the feed to show, such
 *     as Map's popup, which the feed scrolls to, then calls [FeedViewModel.clearPostToShow]. Null
 *     if none was asked for, or [posts] doesn't hold it yet, as while the feed shows the posts it
 *     had before that one was shared.
 */
data class FeedUiState(
    val posts: PostTable = PostTable.EMPTY,
    val isLoading: Boolean = true,
    val rowToShow: Int? = null
)

/**
 * Holds posts for [FeedScreen], and deletes the logged-in user's own posts.
 *
 * @param postRepository Where post data is read from and deleted.
 * @param photoRepository Where a deleted post's photo is removed from.
 * @param accountRepository Tells who is logged in, so only a post's author can delete it.
 * @param showInFeedRequest The post another screen asks the feed to show, which it scrolls to.
 * @param showOnMapRequest Where [showOnMap] asks Map to show a post.
 * @param postsDispatcher Where the row of the post to show is found: off the main thread, since
 *     finding it is a pass over the posts.
 * @param sharing When [uiState] follows the posts: while Feed collects it, in the app.
 */
class FeedViewModel(
    private val postRepository: PostRepository,
    private val photoRepository: PhotoRepository,
    accountRepository: AccountRepository,
    private val showInFeedRequest: ShowPostRequest,
    private val showOnMapRequest: ShowPostRequest,
    postsDispatcher: CoroutineDispatcher,
    sharing: SharingStarted
) : ViewModel() {
    /**
     * The current state of the feed, updated whenever posts change or another screen asks the
     * feed to show a post.
     */
    val uiState: StateFlow<FeedUiState> =
        combine(postRepository.getPostTable(), showInFeedRequest.postId) { posts, postToShow ->
            FeedUiState(posts = posts, isLoading = false, rowToShow = postToShow?.let(posts::rowOf))
        }
            .flowOn(postsDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = sharing,
                initialValue = FeedUiState()
            )

    /** The logged-in user's ID, or null for guests, who can't delete posts. */
    val loggedInUid: StateFlow<String?> = accountRepository.loggedInUid

    /** Forgets the post to show once the feed has scrolled to it, so it doesn't scroll again. */
    fun clearPostToShow() {
        showInFeedRequest.clear()
    }

    /** Asks Map to show the post with [postId], which opens its popup when Map opens. */
    fun showOnMap(postId: String) {
        showOnMapRequest.send(postId)
    }

    /**
     * Deletes the post with [postId], then its photo. Ignored unless the logged-in user wrote it.
     * The feed drops the post on its own, since [uiState] follows the repository's posts.
     */
    fun deletePost(postId: String) {
        val posts = uiState.value.posts
        val row = posts.rowOf(postId) ?: return
        val authorUid = posts.authorUid(row)
        if (authorUid.isEmpty() || authorUid != loggedInUid.value) {
            return
        }
        // Read now: once the post is deleted, the table no longer holds it.
        val imageUri = posts.imageUri(row)

        viewModelScope.launch {
            try {
                postRepository.deletePost(postId)
            } catch (error: FirebaseException) {
                Log.e(TAG, "Could not delete the post", error)
                return@launch
            }

            // A post without an uploaded photo has nothing more to delete.
            if (!imageUri.startsWith("https://")) {
                return@launch
            }
            try {
                photoRepository.deletePhoto(imageUri)
            } catch (error: PhotoDeletionException) {
                // The post is gone, so nobody sees the leftover photo.
                Log.e(TAG, "Post deleted, but its photo wasn't", error)
            }
        }
    }

    companion object {
        private const val TAG = "FeedViewModel"

        /** Creates a [FeedViewModel] that reads the app's shared posts and requests. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                FeedViewModel(
                    postRepository = application.container.postRepository,
                    photoRepository = application.container.photoRepository,
                    accountRepository = application.container.accountRepository,
                    showInFeedRequest = application.container.showInFeedRequest,
                    showOnMapRequest = application.container.showOnMapRequest,
                    postsDispatcher = Dispatchers.Default,
                    sharing = application.container.whileScreenCollects
                )
            }
        }
    }
}
