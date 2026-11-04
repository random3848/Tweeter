package edu.csuci.tweeter.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The UI state of the home feed screen.
 *
 * @property posts The list of posts to display in the feed.
 * @property isLoading Whether posts are currently being loaded from the repository.
 */
data class FeedUiState(val posts: List<Post> = emptyList(), val isLoading: Boolean = true)

/**
 * Holds posts for [FeedScreen].
 *
 * @param postRepository Where post data is read from.
 * @param showInFeedRequest The post another screen asks the feed to show, which it scrolls to.
 * @param showOnMapRequest Where [showOnMap] asks Map to show a post.
 */
class FeedViewModel(
    postRepository: PostRepository,
    private val showInFeedRequest: ShowPostRequest,
    private val showOnMapRequest: ShowPostRequest
) : ViewModel() {
    /**
     * The current state of the feed, updated whenever posts change.
     */
    val uiState: StateFlow<FeedUiState> = postRepository.getPosts()
        .map { posts ->
            FeedUiState(
                posts = posts.sortPosts(FeedSortOrder.MOST_RECENT_FIRST),
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FeedUiState(isLoading = true)
        )

    /**
     * The ID of the post another screen asked the feed to show, such as Map's popup, or null. The
     * feed scrolls to it once its posts include it, then calls [clearPostToShow].
     */
    val postToShow: StateFlow<String?> = showInFeedRequest.postId

    /** Forgets [postToShow] once the feed has scrolled to it, so it doesn't scroll there again. */
    fun clearPostToShow() {
        showInFeedRequest.clear()
    }

    /** Asks Map to show [post], which opens the post's popup when Map opens. */
    fun showOnMap(post: Post) {
        showOnMapRequest.send(post.id)
    }

    companion object {
        /** Creates a [FeedViewModel] that reads the app's shared posts and requests. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                FeedViewModel(
                    postRepository = application.container.postRepository,
                    showInFeedRequest = application.container.showInFeedRequest,
                    showOnMapRequest = application.container.showOnMapRequest
                )
            }
        }
    }
}
