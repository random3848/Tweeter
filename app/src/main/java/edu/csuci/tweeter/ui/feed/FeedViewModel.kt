package edu.csuci.tweeter.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
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
 * Holds posts for [FeedScreen].
 *
 * @param postRepository Where post data is read from.
 * @param showInFeedRequest The post another screen asks the feed to show, which it scrolls to.
 * @param showOnMapRequest Where [showOnMap] asks Map to show a post.
 * @param postsDispatcher Where the row of the post to show is found: off the main thread, since
 *     finding it is a pass over the posts.
 * @param sharing When [uiState] follows the posts: while Feed collects it, in the app.
 */
class FeedViewModel(
    postRepository: PostRepository,
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

    /** Forgets the post to show once the feed has scrolled to it, so it doesn't scroll again. */
    fun clearPostToShow() {
        showInFeedRequest.clear()
    }

    /** Asks Map to show the post with [postId], which opens its popup when Map opens. */
    fun showOnMap(postId: String) {
        showOnMapRequest.send(postId)
    }

    companion object {
        /** Creates a [FeedViewModel] that reads the app's shared posts and requests. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                FeedViewModel(
                    postRepository = application.container.postRepository,
                    showInFeedRequest = application.container.showInFeedRequest,
                    showOnMapRequest = application.container.showOnMapRequest,
                    postsDispatcher = Dispatchers.Default,
                    sharing = application.container.whileScreenCollects
                )
            }
        }
    }
}
