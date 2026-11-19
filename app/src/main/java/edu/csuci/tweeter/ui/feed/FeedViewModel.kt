package edu.csuci.tweeter.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.AnimalVote
import edu.csuci.tweeter.data.FollowRepository
import edu.csuci.tweeter.data.PostBallot
import edu.csuci.tweeter.data.PostDeleter
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostRows
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.ui.ChoicePerUser
import edu.csuci.tweeter.ui.UserChoice
import edu.csuci.tweeter.ui.friends.FriendsReader
import edu.csuci.tweeter.ui.friends.UserFriends
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import edu.csuci.tweeter.ui.voting.PostVoter
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the feed shows: the [choice] in effect, and the [authors] whose posts it shows. */
private data class ShownChoice(val choice: FeedChoice, val authors: FeedAuthors)

/**
 * The UI state of the home feed screen.
 *
 * @property listedPosts The posts the feed shows, in order: ranked when the user picked [choice],
 *     so posts don't move as their likes change.
 * @property choice What the user picked to see, shown checked.
 * @property offeredChoices The choices on the chips: Friends only for users who are logged in.
 * @property authors Whose posts the feed shows, as [choice] decides.
 * @property isLoading Whether the first posts are still being loaded from the repository.
 * @property positionToShow Where in [listedPosts] the post another screen asked the feed to show
 *     is, such as Map's popup's, which the feed scrolls to, then calls
 *     [FeedViewModel.clearPostToShow]. Null if none was asked for, or [listedPosts] don't hold it,
 *     as while the feed shows the posts it had before that one was shared, or when its author
 *     isn't among the friends shown.
 * @property likedPostIds The IDs of the posts the user liked, whose hearts are filled.
 * @property bookmarkedPostIds The IDs of the posts the user saved, whose bookmarks are filled: none
 *     until they're read, since the posts don't wait for them.
 * @property canLike Whether the user can like and save posts: they're logged in, not a guest.
 */
data class FeedUiState(
    val listedPosts: PostRows = PostRows.EMPTY,
    val choice: FeedChoice = FeedChoice.BEST,
    val offeredChoices: List<FeedChoice> = FeedChoice.offeredTo(isLoggedIn = false),
    val authors: FeedAuthors = FeedAuthors.Everyone,
    val isLoading: Boolean = true,
    val positionToShow: Int? = null,
    val likedPostIds: Set<String> = emptySet(),
    val bookmarkedPostIds: Set<String> = emptySet(),
    val canLike: Boolean = false
) {
    /** Every post, newest first, of which the feed shows [listedPosts]. */
    val posts: PostTable
        get() = listedPosts.table
}

/**
 * Holds posts for [FeedScreen], as the user chooses to see them, the user's likes and bookmarks of
 * them and votes on their animals, and deletes the logged-in user's own posts.
 *
 * @param postRepository Where posts are read from.
 * @param postLiker Which posts the user liked, and saves the likes [toggleLike] makes.
 * @param postVoter The user's votes on what animal posts show, and saves the changes
 *     [toggleVote] and [reclassify] make.
 * @param postBookmarker Which posts the user saved, and saves the bookmarks [toggleBookmark] makes.
 * @param postDeleter Deletes the logged-in user's posts and their photos.
 * @param accountRepository Who is logged in: guests can't like or save posts, and only the user's
 *     own posts show a delete button.
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
    private val postLiker: PostLiker,
    private val postVoter: PostVoter,
    private val postBookmarker: PostBookmarker,
    private val postDeleter: PostDeleter,
    accountRepository: AccountRepository,
    followRepository: FollowRepository,
    private val showInFeedRequest: ShowPostRequest,
    private val showOnMapRequest: ShowPostRequest,
    postsDispatcher: CoroutineDispatcher,
    private val clock: Clock,
    sharing: SharingStarted
) : ViewModel() {
    private val choice = ChoicePerUser(accountRepository, default = FeedChoice.BEST)

    private val friendsReader = FriendsReader(followRepository)

    // What the user or guest chose and whose posts it shows, reading the user's friends for
    // Friends. Collected once, in [uiState], so the friends are read once.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val shown: Flow<ShownChoice> = choice.currentChoice().flatMapLatest { chosen ->
        val shownChoice = shownChoiceOf(chosen)
        authorsFor(chosen.uid, shownChoice).map { authors -> ShownChoice(shownChoice, authors) }
    }

    // The order alone, which doesn't depend on who the user's friends are, so it's found without
    // reading them.
    private val order: Flow<FeedOrder> = choice.currentChoice()
        .map { chosen -> shownChoiceOf(chosen).order }
        .distinctUntilChanged()

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
     * their friends change, another screen asks the feed to show a post, or the user likes or saves
     * a post or logs in or out.
     */
    val uiState: StateFlow<FeedUiState> =
        combine(
            rankedPosts,
            shown,
            showInFeedRequest.postId,
            postLiker.likedPostIds(),
            accountRepository.loggedInUid
        ) { ranked, shown, postToShow, likedPostIds, loggedInUid ->
            val listedPosts = shown.authors.listedIn(ranked.listedPosts)
            FeedUiState(
                listedPosts = listedPosts,
                choice = shown.choice,
                offeredChoices = FeedChoice.offeredTo(isLoggedIn = loggedInUid != null),
                authors = shown.authors,
                isLoading = false,
                positionToShow = postToShow?.let { postId -> listedPosts.positionOf(postId) },
                likedPostIds = likedPostIds,
                canLike = loggedInUid != null
            )
        }
            .combine(postBookmarker.bookmarkedPostIdsStartingEmpty()) { state, bookmarkedPostIds ->
                state.copy(bookmarkedPostIds = bookmarkedPostIds)
            }
            .flowOn(postsDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = sharing,
                initialValue = FeedUiState()
            )

    /** The logged-in user's ID, or null for guests, who can't delete posts. */
    val loggedInUid: StateFlow<String?> = accountRepository.loggedInUid

    /** The user's votes on what animal posts show, by post ID; none for guests. */
    val userVotes: StateFlow<Map<String, AnimalVote>> =
        postVoter.userVotes().stateIn(viewModelScope, sharing, emptyMap())

    /** Shows the posts as [choice] picks them from now on, ranked again in its order. */
    fun choose(choice: FeedChoice) {
        this.choice.choose(choice)
    }

    /** Reads the user's friends again, if reading them failed. */
    fun retryFriends() {
        friendsReader.retry()
    }

    // What the feed shows for [chosen]: the choice itself, except Best for a guest on Friends,
    // since guests have no friends.
    private fun shownChoiceOf(chosen: UserChoice<FeedChoice>): FeedChoice {
        val isGuestOnFriends = chosen.uid == null && chosen.value.showsOnlyFriends
        return if (isGuestOnFriends) FeedChoice.BEST else chosen.value
    }

    private fun authorsFor(uid: String?, shownChoice: FeedChoice): Flow<FeedAuthors> =
        if (uid != null && shownChoice.showsOnlyFriends) {
            friendsReader.friendsOf(uid).map(::feedAuthorsOf)
        } else {
            flowOf(FeedAuthors.Everyone)
        }

    private fun feedAuthorsOf(friends: UserFriends): FeedAuthors = when (friends) {
        is UserFriends.Known -> FeedAuthors.Friends(friends.uids)
        UserFriends.Loading -> FeedAuthors.LoadingFriends
        UserFriends.Unknown -> FeedAuthors.UnknownFriends
    }

    // Where in these rows the post with [postId] is, or null if they don't hold it.
    private fun PostRows.positionOf(postId: String): Int? {
        val row = table.rowOf(postId) ?: return null
        return rows.indexOf(row).takeIf { position -> position >= 0 }
    }

    /** Forgets the post to show once the feed has scrolled to it, so it doesn't scroll again. */
    fun clearPostToShow() {
        showInFeedRequest.clear()
    }

    /**
     * Likes the post with [postId], or takes the like back if the user liked it, unless a change
     * to its like is still being saved. A change that can't be saved is logged; the heart then
     * shows the like as it was.
     */
    fun toggleLike(postId: String) {
        val isLiked = postId in uiState.value.likedPostIds
        viewModelScope.launch {
            if (isLiked) postLiker.unlike(postId) else postLiker.like(postId)
        }
    }

    /**
     * Votes for [animal] instead of the [ballot]'s vote on a post's animal, or takes the vote back
     * if it already names [animal], unless a change to the post's votes is still being saved. A
     * change that can't be saved is logged; the post then shows the vote as it was.
     */
    fun toggleVote(ballot: PostBallot, animal: String) {
        viewModelScope.launch { postVoter.toggleVote(ballot, animal) }
    }

    /**
     * Has the user's post with [postId] name [animal] instead, which clears its votes, unless a
     * change to its votes is still being saved.
     */
    fun reclassify(postId: String, animal: String) {
        viewModelScope.launch { postVoter.reclassify(postId, animal) }
    }

    /**
     * Saves the post with [postId], or unsaves it if the user saved it, unless a change to its
     * bookmark is still being saved. A change that can't be saved is logged; the bookmark then
     * shows the post as it was.
     */
    fun toggleBookmark(postId: String) {
        val isBookmarked = postId in uiState.value.bookmarkedPostIds
        viewModelScope.launch {
            if (isBookmarked) postBookmarker.unbookmark(postId) else postBookmarker.bookmark(postId)
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
        /**
         * Creates a [FeedViewModel] that reads the app's shared posts, likes, votes, bookmarks and
         * requests.
         */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                FeedViewModel(
                    postRepository = application.container.postRepository,
                    postLiker = PostLiker(application.container.likeRepository),
                    postVoter = PostVoter(application.container.voteRepository),
                    postBookmarker = PostBookmarker(application.container.bookmarkRepository),
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
