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
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the posts [MapScreen] shows as pins.
 *
 * @param postRepository Where the posts come from.
 */
class MapViewModel(postRepository: PostRepository) : ViewModel() {
    /** Every post, newest first; empty until the repository first emits. */
    val posts: StateFlow<List<Post>> = postRepository.getPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        /** Creates a [MapViewModel] that reads the app's shared [PostRepository]. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                MapViewModel(application.container.postRepository)
            }
        }
    }
}
