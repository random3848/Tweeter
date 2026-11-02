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
import edu.csuci.tweeter.ui.navigation.PostSelection
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
 * @param feedSelection The post another screen asked the feed to show, which it scrolls to.
 * @param mapSelection The post whose popup is open on Map, which [showOnMap] selects.
 */
class FeedViewModel(
    postRepository: PostRepository,
    private val feedSelection: PostSelection,
    private val mapSelection: PostSelection
) : ViewModel() {
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

    /**
     * The ID of the post another screen asked the feed to show, such as Map's popup, or null. The
     * feed scrolls to it, then calls [clearPostToShow].
     */
    val postToShow: StateFlow<String?> = feedSelection.postId

    /** Forgets [postToShow] once the feed has scrolled to it, so it doesn't scroll there again. */
    fun clearPostToShow() {
        feedSelection.clear()
    }

    /** Selects [post] on Map, so that Map opens its popup when it opens. */
    fun showOnMap(post: Post) {
        mapSelection.select(post.id)
    }

    companion object {
        /** Creates a [FeedViewModel] that reads the app's shared posts and selections. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                FeedViewModel(
                    postRepository = application.container.postRepository,
                    feedSelection = application.container.feedSelection,
                    mapSelection = application.container.mapSelection
                )
            }
        }
    }
}
