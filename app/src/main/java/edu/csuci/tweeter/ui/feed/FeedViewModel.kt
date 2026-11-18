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
import edu.csuci.tweeter.data.FollowException
import edu.csuci.tweeter.data.FollowRepository
import edu.csuci.tweeter.data.LikeException
import edu.csuci.tweeter.data.LikeRepository
import edu.csuci.tweeter.data.PostDeleter
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.observeFriendUids
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "FeedViewModel"

/**
 * What the feed shows: the [choice] in effect, and the [authors] whose posts it shows.
 */
private data class ShownChoice(val choice: FeedChoice, val authors: FeedAuthors)

/**
 * The UI state of the home feed screen.
 *
 * @property posts The posts to display, newest first.
 * @property choice What the user picked to see, shown checked.
 * @property offeredChoices The choices on the chips: Friends only for users who are logged in.
 * @property authors Whose posts the feed shows, as [choice] decides.
 * @property rows The rows of [posts] the feed shows, in order: ranked when the user picked
 *     [choice], so posts don't move as their likes change.
 * @property isLoading Whether the first posts are still being loaded from the repository.
 * @property positionToShow Where in [rows] the post another screen asked the feed to show is, such
 *     as Map's popup's, which the feed scrolls to, then calls [FeedViewModel.clearPostToShow]. Null
 *     if none was asked for, or [rows] don't hold it, as while the feed shows the posts it had
 *     before that one was shared, or when its author isn't among the friends shown.
 * @property likedPostIds The IDs of the posts the user liked, whose hearts are filled.
 * @property canLike Whether the user can like posts: they're logged in, not a guest.
 */
data class FeedUiState(
    val posts: PostTable = PostTable.EMPTY,
    val choice: FeedChoice = FeedChoice.BEST,
    val offeredChoices: List<FeedChoice> = FeedChoice.offeredTo(isLoggedIn = false),
    val authors: FeedAuthors = FeedAuthors.Everyone,
    val rows: IntArray = IntArray(0),
    val isLoading: Boolean = true,
    val positionToShow: Int? = null,
    val likedPostIds: Set<String> = emptySet(),
    val canLike: Boolean = false
)

/**
 * Holds posts for [FeedScreen], as the user chooses to see them, the user's likes of them, and
 * deletes the logged-in user's own posts.
 *
 * @param postRepository Where posts are read from.
 * @param likeRepository Which posts the user liked, and where [toggleLike] saves likes.
 * @param postDeleter Deletes the logged-in user's posts and their photos.
 * @param accountRepository Who is logged in: guests can't like posts, and only the user's own posts
 *     show a delete button.
 * @param followRepository Who the user's friends are, for [FeedChoice.FRIENDS].
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
    private val followRepository: FollowRepository,
    private val showInFeedRequest: ShowPostRequest,
    private val showOnMapRequest: ShowPostRequest,
    postsDispatcher: CoroutineDispatcher,
    private val clock: Clock,
    sharing: SharingStarted
) : ViewModel() {
    private val chosen = MutableStateFlow(FeedChoice.BEST)

    // How many times the user asked to read their friends again after reading them failed.
    private val friendsRetryCount = MutableStateFlow(0)

    // What the user chose and whose posts it shows. Guests, who have no friends, see Best instead
    // of Friends; their choice is kept for when they log in.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val shown: Flow<ShownChoice> =
        combine(accountRepository.loggedInUid, chosen, ::Pair)
            .flatMapLatest { (uid, chosen) ->
                if (uid == null && chosen.showsOnlyFriends) {
                    flowOf(ShownChoice(FeedChoice.BEST, FeedAuthors.Everyone))
                } else {
                    authorsFor(chosen, uid).map { authors -> ShownChoice(chosen, authors) }
                }
            }

    private val order: Flow<FeedOrder> = shown.map { it.choice.order }.distinctUntilChanged()

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
     * The current state of the feed, updated whenever posts change, the user picks what to see,
     * their friends change, another screen asks the feed to show a post, or the user likes a post
     * or logs in or out.
     */
    val uiState: StateFlow<FeedUiState> =
        combine(
            rankedPosts,
            shown,
            showInFeedRequest.postId,
            likeRepository.getLikedPostIds(),
            accountRepository.loggedInUid
        ) { ranked, shown, postToShow, likedPostIds, loggedInUid ->
            val rows = shown.authors.rowsIn(ranked.posts, ranked.rows)
            FeedUiState(
                posts = ranked.posts,
                choice = shown.choice,
                offeredChoices = FeedChoice.offeredTo(isLoggedIn = loggedInUid != null),
                authors = shown.authors,
                rows = rows,
                isLoading = false,
                positionToShow = postToShow?.let { postId -> positionOf(postId, ranked, rows) },
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

    /** Shows the posts as [choice] picks them from now on, ranked again in its order. */
    fun choose(choice: FeedChoice) {
        chosen.value = choice
    }

    /** Reads the user's friends again, if reading them failed. */
    fun retryFriends() {
        friendsRetryCount.update { it + 1 }
    }

    private fun authorsFor(choice: FeedChoice, uid: String?): Flow<FeedAuthors> =
        if (choice.showsOnlyFriends && uid != null) friendsOf(uid) else flowOf(FeedAuthors.Everyone)

    // Loading until they are read. Once they can't be, unknown until the user asks to retry.
    private fun friendsOf(uid: String): Flow<FeedAuthors> = followRepository.observeFriendUids(uid)
        .map<Set<String>, FeedAuthors> { friendUids -> FeedAuthors.Friends(friendUids) }
        .onStart { emit(FeedAuthors.LoadingFriends) }
        .retryWhen { error, _ ->
            if (error !is FollowException) {
                return@retryWhen false
            }
            Log.e(TAG, "Could not read the user's friends", error)
            // Read before saying so, so a retry asked for once the user sees it isn't missed.
            val retriesSoFar = friendsRetryCount.value
            emit(FeedAuthors.UnknownFriends)
            friendsRetryCount.first { it > retriesSoFar }
            true
        }

    // Where in [rows] the post with [postId] is, or null if they don't hold it.
    private fun positionOf(postId: String, ranked: RankedPosts, rows: IntArray): Int? {
        val row = ranked.posts.rowOf(postId) ?: return null
        return rows.indexOf(row).takeIf { position -> position >= 0 }
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
                    followRepository = application.container.followRepository,
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
