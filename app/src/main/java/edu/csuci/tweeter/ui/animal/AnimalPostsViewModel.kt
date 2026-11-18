package edu.csuci.tweeter.ui.animal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.PostDeleter
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostRows
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.ui.feed.PostLiker
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What [AnimalPostsScreen] shows.
 *
 * @property listedPosts The posts naming the animal, newest first.
 * @property isLoading Whether the posts are still being read.
 * @property likedPostIds The IDs of the posts the user liked, whose hearts are filled.
 * @property canLike Whether the user can like posts: they're logged in, not a guest.
 */
data class AnimalPostsUiState(
    val listedPosts: PostRows = PostRows.EMPTY,
    val isLoading: Boolean = true,
    val likedPostIds: Set<String> = emptySet(),
    val canLike: Boolean = false
)

/**
 * Holds the posts that name one animal, for [AnimalPostsScreen], and the user's likes of them,
 * and deletes the logged-in user's own posts, as Feed does.
 *
 * @param animal The animal's name, as [PostTable.rowsOfAnimal] matches it.
 * @param postRepository Where the posts come from.
 * @param postDeleter Deletes the logged-in user's posts and their photos.
 * @param accountRepository Who is logged in: guests can't like posts, and only the user's own posts
 *     show a delete button.
 * @param postLiker Which posts the user liked, and saves the likes [toggleLike] makes.
 * @param showOnMapRequest Where [showOnMap] asks Map to show a post.
 * @param rowsDispatcher Where the animal's rows are found: off the main thread, since finding
 *     them is a pass over every post.
 * @param sharing When [uiState] follows the posts: while the screen collects it, in the app.
 */
class AnimalPostsViewModel(
    animal: String,
    postRepository: PostRepository,
    private val postDeleter: PostDeleter,
    accountRepository: AccountRepository,
    private val postLiker: PostLiker,
    private val showOnMapRequest: ShowPostRequest,
    rowsDispatcher: CoroutineDispatcher,
    sharing: SharingStarted
) : ViewModel() {
    // The animal's posts, found once per table, not again when only likes change.
    private val animalPosts: Flow<PostRows> = postRepository.getPostTable()
        .map { posts -> PostRows(posts, posts.rowsOfAnimal(animal)) }

    /** The animal's posts, updated as posts are shared or change, or the user likes a post. */
    val uiState: StateFlow<AnimalPostsUiState> = combine(
        animalPosts,
        postLiker.likedPostIds(),
        accountRepository.loggedInUid
    ) { listedPosts, likedPostIds, loggedInUid ->
        AnimalPostsUiState(
            listedPosts = listedPosts,
            isLoading = false,
            likedPostIds = likedPostIds,
            canLike = loggedInUid != null
        )
    }
        .flowOn(rowsDispatcher)
        .stateIn(viewModelScope, sharing, AnimalPostsUiState())

    /** The logged-in user's ID, or null for guests, who can't delete posts. */
    val loggedInUid: StateFlow<String?> = accountRepository.loggedInUid

    /**
     * Likes the post with [postId], or takes the like back if the user liked it, unless a change
     * to its like is still being saved.
     */
    fun toggleLike(postId: String) {
        val isLiked = postId in uiState.value.likedPostIds
        viewModelScope.launch {
            if (isLiked) postLiker.unlike(postId) else postLiker.like(postId)
        }
    }

    /** Asks Map to show the post with [postId], which opens its popup when Map opens. */
    fun showOnMap(postId: String) {
        showOnMapRequest.send(postId)
    }

    /**
     * Deletes the post with [postId], then its photo, if the logged-in user wrote it. The list
     * drops the post on its own, since [uiState] follows the repository's posts.
     */
    fun deletePost(postId: String) {
        postDeleter.deleteOwnPost(uiState.value.listedPosts.table, postId)
    }

    companion object {
        /**
         * Creates an [AnimalPostsViewModel] that reads the app's shared posts and likes.
         *
         * @param animal The animal whose posts it holds.
         */
        fun createFactory(animal: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                AnimalPostsViewModel(
                    animal = animal,
                    postRepository = application.container.postRepository,
                    postDeleter = application.container.postDeleter,
                    accountRepository = application.container.accountRepository,
                    postLiker = PostLiker(application.container.likeRepository),
                    showOnMapRequest = application.container.showOnMapRequest,
                    rowsDispatcher = Dispatchers.Default,
                    sharing = application.container.whileScreenCollects
                )
            }
        }
    }
}
