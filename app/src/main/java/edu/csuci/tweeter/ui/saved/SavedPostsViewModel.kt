package edu.csuci.tweeter.ui.saved

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
import edu.csuci.tweeter.ui.feed.PostBookmarker
import edu.csuci.tweeter.ui.feed.PostLiker
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What [SavedPostsScreen] shows.
 *
 * @property listedPosts The posts the user saved, newest first.
 * @property isLoading Whether the posts are still being read.
 * @property likedPostIds The IDs of the posts the user liked, whose hearts are filled.
 * @property bookmarkedPostIds The IDs of the posts the user saved, whose bookmarks are filled.
 * @property canLike Whether the user can like and save posts: they're logged in, not a guest.
 */
data class SavedPostsUiState(
    val listedPosts: PostRows = PostRows.EMPTY,
    val isLoading: Boolean = true,
    val likedPostIds: Set<String> = emptySet(),
    val bookmarkedPostIds: Set<String> = emptySet(),
    val canLike: Boolean = false
)

/** The saved posts the screen lists, and the IDs of every post the user saved. */
private data class SavedPosts(val listedPosts: PostRows, val bookmarkedPostIds: Set<String>)

/**
 * Holds the posts the logged-in user saved, for [SavedPostsScreen], and the user's likes of them,
 * and deletes the logged-in user's own posts, as Feed does.
 *
 * @param postRepository Where the posts come from.
 * @param postBookmarker Which posts the user saved, and saves the bookmarks [toggleBookmark] makes.
 * @param postLiker Which posts the user liked, and saves the likes [toggleLike] makes.
 * @param postDeleter Deletes the logged-in user's posts and their photos.
 * @param accountRepository Who is logged in: guests can't like or save posts, and only the user's
 *     own posts show a delete button.
 * @param showOnMapRequest Where [showOnMap] asks Map to show a post.
 * @param rowsDispatcher Where the saved posts' rows are found: off the main thread, since finding
 *     them is a pass over every post.
 * @param sharing When [uiState] follows the posts: while the screen collects it, in the app.
 */
class SavedPostsViewModel(
    postRepository: PostRepository,
    private val postBookmarker: PostBookmarker,
    private val postLiker: PostLiker,
    private val postDeleter: PostDeleter,
    accountRepository: AccountRepository,
    private val showOnMapRequest: ShowPostRequest,
    rowsDispatcher: CoroutineDispatcher,
    sharing: SharingStarted
) : ViewModel() {
    // The saved posts, found once per table or change of bookmarks, not again when only likes
    // change.
    private val savedPosts: Flow<SavedPosts> =
        combine(postRepository.getPostTable(), postBookmarker.bookmarkedPostIds()) { posts, ids ->
            SavedPosts(PostRows(posts, posts.rowsOf(ids)), bookmarkedPostIds = ids)
        }

    /**
     * The saved posts, updated as posts change or are deleted, or the user likes, saves or unsaves
     * a post.
     */
    val uiState: StateFlow<SavedPostsUiState> = combine(
        savedPosts,
        postLiker.likedPostIds(),
        accountRepository.loggedInUid
    ) { saved, likedPostIds, loggedInUid ->
        SavedPostsUiState(
            listedPosts = saved.listedPosts,
            isLoading = false,
            likedPostIds = likedPostIds,
            bookmarkedPostIds = saved.bookmarkedPostIds,
            canLike = loggedInUid != null
        )
    }
        .flowOn(rowsDispatcher)
        .stateIn(viewModelScope, sharing, SavedPostsUiState())

    /** The logged-in user's ID, or null for guests, who can't delete posts. */
    val loggedInUid: StateFlow<String?> = accountRepository.loggedInUid

    /**
     * Unsaves the post with [postId], which drops it from the list, or saves it again if the user
     * unsaved it, unless a change to its bookmark is still being saved.
     */
    fun toggleBookmark(postId: String) {
        val isBookmarked = postId in uiState.value.bookmarkedPostIds
        viewModelScope.launch {
            if (isBookmarked) postBookmarker.unbookmark(postId) else postBookmarker.bookmark(postId)
        }
    }

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
         * Creates a [SavedPostsViewModel] that reads the app's shared posts, bookmarks and likes.
         */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                SavedPostsViewModel(
                    postRepository = application.container.postRepository,
                    postBookmarker = PostBookmarker(application.container.bookmarkRepository),
                    postLiker = PostLiker(application.container.likeRepository),
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
