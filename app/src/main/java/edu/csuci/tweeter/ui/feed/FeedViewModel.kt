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
 * The UI state of the home photo feed screen.
 *
 * @property photoPosts The list of photo-only posts to display in the feed.
 */
data class FeedUiState(val photoPosts: List<Post> = emptyList())

/**
 * Holds photo posts for [FeedScreen].
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
                photoPosts = posts
                    .filter { it.imageUri.isNotBlank() }
                    .sortPosts(FeedSortOrder.MOST_RECENT_FIRST)
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FeedUiState()
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
