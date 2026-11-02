package edu.csuci.tweeter.ui.map

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the posts [MapScreen] shows as pins, and the one whose popup is open.
 *
 * @param postRepository Where the posts come from.
 * @param mapSelection The post whose popup is open, which Feed also selects to show a post here.
 * @param feedSelection The post Feed scrolls to when it opens, which [showInFeed] selects.
 */
class MapViewModel(
    postRepository: PostRepository,
    private val mapSelection: PostSelection,
    private val feedSelection: PostSelection
) : ViewModel() {
    /** Every post, newest first; empty until the repository first emits. */
    val posts: StateFlow<List<Post>> = postRepository.getPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The post whose popup is open, or null if none is or it isn't among [posts]. */
    val selectedPost: StateFlow<Post?> = combine(posts, mapSelection.postId) { posts, postId ->
        posts.find { it.id == postId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Opens the popup of the post with [postId], closing any other. */
    fun selectPost(postId: String) {
        mapSelection.select(postId)
    }

    /** Closes the popup. */
    fun clearSelection() {
        mapSelection.clear()
    }

    /** Selects [post] for Feed, which scrolls to it when it opens. */
    fun showInFeed(post: Post) {
        feedSelection.select(post.id)
    }

    companion object {
        /** Creates a [MapViewModel] that reads the app's shared posts and selections. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                MapViewModel(
                    postRepository = application.container.postRepository,
                    mapSelection = application.container.mapSelection,
                    feedSelection = application.container.feedSelection
                )
            }
        }
    }
}
