package edu.csuci.tweeter.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.Avatar
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostRepository
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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
 * @param photoRepository Where Firebase photos are fetched from.
 */
class FeedViewModel(postRepository: PostRepository, private val photoRepository: PhotoRepository) :
    ViewModel() {
    // Temporary: reads Firebase Storage while there's no post database. Removed when
    // FakePostRepository() in AppContainer is replaced with the real database; at that point
    // the feed should read only postRepository.getPosts(), and this flow, the combine,
    // and PhotoRepository.fetchPhotoUrls() can be deleted.
    private val firebasePhotosFlow = flow {
        val urls = photoRepository.fetchPhotoUrls()
        val posts = urls.mapIndexed { index, url ->
            Post(
                id = "firebase-$index-$url",
                username = "firebase",
                avatar = Avatar(initials = "FB"),
                locationName = "",
                latitude = 0.0,
                longitude = 0.0,
                imageUri = url,
                caption = "",
                likeCount = 0,
                commentCount = 0,
                isLiked = false,
                isBookmarked = false,
                timestamp = Instant.now()
            )
        }
        emit(posts)
    }

    /**
     * The current state of the feed, updated whenever posts change.
     */
    val uiState: StateFlow<FeedUiState> = combine(
        postRepository.getPosts(),
        firebasePhotosFlow
    ) { repoPosts, firebasePosts ->
        val localPhotoPosts = repoPosts.filter { it.imageUri.isNotBlank() }
        val existingUris = localPhotoPosts.map { it.imageUri }.toSet()
        val uniqueFirebasePosts = firebasePosts.filter { it.imageUri !in existingUris }
        val combinedPosts = localPhotoPosts + uniqueFirebasePosts

        FeedUiState(
            photoPosts = combinedPosts.sortPosts(FeedSortOrder.MOST_RECENT_FIRST)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FeedUiState()
    )

    companion object {
        /** Creates a [FeedViewModel] that reads the app's shared repositories. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                FeedViewModel(
                    postRepository = application.container.postRepository,
                    photoRepository = application.container.photoRepository
                )
            }
        }
    }
}
