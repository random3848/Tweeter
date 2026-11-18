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
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What [AnimalPostsScreen] shows.
 *
 * @property listedPosts The posts naming the animal, newest first.
 * @property isLoading Whether the posts are still being read.
 */
data class AnimalPostsUiState(
    val listedPosts: PostRows = PostRows.EMPTY,
    val isLoading: Boolean = true
)

/**
 * Holds the posts that name one animal, for [AnimalPostsScreen], and deletes the logged-in user's
 * own posts, as Feed does.
 *
 * @param animal The animal's name, as [PostTable.rowsOfAnimal] matches it.
 * @param postRepository Where the posts come from.
 * @param postDeleter Deletes the logged-in user's posts and their photos.
 * @param accountRepository Tells who is logged in, so only their posts show a delete button.
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
    private val showOnMapRequest: ShowPostRequest,
    rowsDispatcher: CoroutineDispatcher,
    sharing: SharingStarted
) : ViewModel() {
    /** The animal's posts, updated as posts are shared or change. */
    val uiState: StateFlow<AnimalPostsUiState> = postRepository.getPostTable()
        .map { posts ->
            AnimalPostsUiState(
                listedPosts = PostRows(posts, posts.rowsOfAnimal(animal)),
                isLoading = false
            )
        }
        .flowOn(rowsDispatcher)
        .stateIn(viewModelScope, sharing, AnimalPostsUiState())

    /** The logged-in user's ID, or null for guests, who can't delete posts. */
    val loggedInUid: StateFlow<String?> = accountRepository.loggedInUid

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
         * Creates an [AnimalPostsViewModel] that reads the app's shared posts.
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
                    showOnMapRequest = application.container.showOnMapRequest,
                    rowsDispatcher = Dispatchers.Default,
                    sharing = application.container.whileScreenCollects
                )
            }
        }
    }
}
