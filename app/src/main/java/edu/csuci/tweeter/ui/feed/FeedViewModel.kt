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
 */
class FeedViewModel(postRepository: PostRepository) : ViewModel() {
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

    companion object {
        /** Creates a [FeedViewModel] that reads the app's shared post repository. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                FeedViewModel(postRepository = application.container.postRepository)
            }
        }
    }
}
