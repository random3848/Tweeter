package edu.csuci.tweeter.ui.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.LikeException
import edu.csuci.tweeter.data.LikeRepository
import edu.csuci.tweeter.data.PostDeleter
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "FeedViewModel"

/**
 * The UI state of the home feed screen.
 *
 * @property posts The posts to display, newest first.
 * @property order The order the user picked, which [rows] are in.
 * @property rows Every row of [posts], in the order the feed shows them: ranked when the user
 *     picked [order], so posts don't move as their likes change.
 * @property isLoading Whether the first posts are still being loaded from the repository.
 * @property positionToShow Where in [rows] the post another screen asked the feed to show is, such
 *     as Map's popup's, which the feed scrolls to, then calls [FeedViewModel.clearPostToShow]. Null
 *     if none was asked for, or [posts] doesn't hold it yet, as while the feed shows the posts it
 *     had before that one was shared.
 * @property likedPostIds The IDs of the posts the user liked, whose hearts are filled.
 * @property canLike Whether the user can like posts: they're logged in, not a guest.
 */
data class FeedUiState(
    val posts: PostTable = PostTable.EMPTY,
    val order: FeedOrder = FeedOrder.BEST,
    val rows: IntArray = IntArray(0),
    val isLoading: Boolean = true,
    val positionToShow: Int? = null,
    val likedPostIds: Set<String> = emptySet(),
    val canLike: Boolean = false
)

/**
 * Holds posts for [FeedScreen], the user's likes of them, and deletes the logged-in user's own
 * posts.
 *
 * @param postRepository Where posts are read from.
 * @param likeRepository Which posts the user liked, and where [toggleLike] saves likes.
 * @param postDeleter Deletes the logged-in user's posts and their photos.
 * @param accountRepository Who is logged in: guests can't like posts, and only the user's own posts
 *     show a delete button.
 * @param showInFeedRequest The post another screen asks the feed to show, which it scrolls to.
 * @param showOnMapRequest Where [showOnMap] asks Map to show a post.
 * @param postsDispatcher Where posts are put in order and the post to show is found: off the main
 *     thread, since each is a pass over the posts.
 * @param clock The time posts are aged by for [FeedOrder.BEST].
 * @param sharing When [uiState] follows the posts: while Feed collects it, in the app.
 */
class FeedViewModel(
    postRepository: PostRepository,
    private val likeRepository: LikeRepository,
    private val postDeleter: PostDeleter,
    accountRepository: AccountRepository,
    private val showInFeedRequest: ShowPostRequest,
    private val showOnMapRequest: ShowPostRequest,
    postsDispatcher: CoroutineDispatcher,
    private val clock: Clock,
    sharing: SharingStarted
) : ViewModel() {
    private val order = MutableStateFlow(FeedOrder.BEST)

    // Ranked when the user picks an order, and kept for every new table until they pick another.
    private val rankedPosts: Flow<RankedPosts> =
        combine(postRepository.getPostTable(), order, ::Pair)
            .runningFold(null as RankedPosts?) { previous, (posts, order) ->
                if (previous?.order == order) {
                    previous.withPosts(posts)
                } else {
                    RankedPosts.of(posts, order, clock.millis())
                }
            }
            .filterNotNull()

    /**
     * The current state of the feed, updated whenever posts change, the user picks an order,
     * another screen asks the feed to show a post, or the user likes a post or logs in or out.
     */
    val uiState: StateFlow<FeedUiState> =
        combine(
            rankedPosts,
            showInFeedRequest.postId,
            likeRepository.getLikedPostIds(),
            accountRepository.loggedInUid
        ) { ranked, postToShow, likedPostIds, loggedInUid ->
            FeedUiState(
                posts = ranked.posts,
                order = ranked.order,
                rows = ranked.rows,
                isLoading = false,
                positionToShow = postToShow?.let(ranked::positionOf),
                likedPostIds = likedPostIds,
                canLike = loggedInUid != null
            )
        }
            .flowOn(postsDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = sharing,
                initialValue = FeedUiState()
            )

    /** The logged-in user's ID, or null for guests, who can't delete posts. */
    val loggedInUid: StateFlow<String?> = accountRepository.loggedInUid

    /** Ranks the posts in [order] and shows them so from now on. */
    fun selectOrder(order: FeedOrder) {
        this.order.value = order
    }

    /** Forgets the post to show once the feed has scrolled to it, so it doesn't scroll again. */
    fun clearPostToShow() {
        showInFeedRequest.clear()
    }

    /**
     * Likes the post with [postId], or takes the like back if the user liked it. A change that
     * can't be saved is logged; the heart then shows the like as it was.
     */
    fun toggleLike(postId: String) {
        val isLiked = postId in uiState.value.likedPostIds
        viewModelScope.launch {
            try {
                if (isLiked) likeRepository.unlike(postId) else likeRepository.like(postId)
            } catch (error: LikeException) {
                Log.e(TAG, "Could not change the like of post $postId", error)
            }
        }
    }

    /** Asks Map to show the post with [postId], which opens its popup when Map opens. */
    fun showOnMap(postId: String) {
        showOnMapRequest.send(postId)
    }

    /**
     * Deletes the post with [postId], then its photo, if the logged-in user wrote it. The feed
     * drops the post on its own, since [uiState] follows the repository's posts.
     */
    fun deletePost(postId: String) {
        postDeleter.deleteOwnPost(uiState.value.posts, postId)
    }

    companion object {
        /** Creates a [FeedViewModel] that reads the app's shared posts, likes and requests. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                FeedViewModel(
                    postRepository = application.container.postRepository,
                    likeRepository = application.container.likeRepository,
                    postDeleter = application.container.postDeleter,
                    accountRepository = application.container.accountRepository,
                    showInFeedRequest = application.container.showInFeedRequest,
                    showOnMapRequest = application.container.showOnMapRequest,
                    postsDispatcher = Dispatchers.Default,
                    clock = Clock.systemUTC(),
                    sharing = application.container.whileScreenCollects
                )
            }
        }
    }
}
