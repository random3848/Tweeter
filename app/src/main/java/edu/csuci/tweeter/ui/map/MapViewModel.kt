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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the posts [MapScreen] shows as pins, and which one is selected.
 *
 * @param postRepository Where the posts come from.
 * @param mapSelection Which post is selected, shared with the screens that show posts on Map.
 */
class MapViewModel(postRepository: PostRepository, private val mapSelection: MapSelection) :
    ViewModel() {
    /** Every post, newest first; empty until the repository first emits. */
    val posts: StateFlow<List<Post>> = postRepository.getPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The selected post, or null if none is selected or it isn't among [posts]. */
    val selectedPost: StateFlow<Post?> = combine(posts, mapSelection.postId) { posts, postId ->
        posts.find { it.id == postId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Selects the post with [postId]. */
    fun selectPost(postId: String) {
        mapSelection.select(postId)
    }

    /** Clears the selection. */
    fun clearSelection() {
        mapSelection.clear()
    }

    companion object {
        /** Creates a [MapViewModel] that reads the app's shared posts and selection. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                MapViewModel(
                    postRepository = application.container.postRepository,
                    mapSelection = application.container.mapSelection
                )
            }
        }
    }
}
