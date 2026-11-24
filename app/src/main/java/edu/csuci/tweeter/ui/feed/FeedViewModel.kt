package edu.csuci.tweeter.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.FollowRepository
import edu.csuci.tweeter.data.PostDeleter
import edu.csuci.tweeter.data.PostLookup
import edu.csuci.tweeter.data.PostLookupException
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostRows
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import edu.csuci.tweeter.ui.ChoicePerUser
import edu.csuci.tweeter.ui.UserChoice
import edu.csuci.tweeter.ui.friends.FriendsReader
import edu.csuci.tweeter.ui.friends.UserFriends
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/** What the feed shows: the [choice] in effect, and the [authors] whose posts it shows. */
private data class ShownChoice(val choice: FeedChoice, val authors: FeedAuthors)

/**
 * The posts the feed lists, as [shown] picks them, and the post another screen asked it to show.
 *
 * @property postToShow The ID of the post asked for, or null while none is.
 */
private class ListedFeed(
    val shown: ShownChoice,
    val listedPosts: PostRows,
    val postToShow: String?
) {
    /** Where in [listedPosts] the post to show is, or null if none is asked for or listed. */
    val positionToShow: Int? = postToShow?.let(::positionOf)

    /** The post to show, if the feed doesn't list it, or null. */
    val unlistedPostToShow: String?
        get() = postToShow.takeIf { positionToShow == null }

    private fun positionOf(postId: String): Int? {
        val row = listedPosts.table.rowOf(postId) ?: return null
        return listedPosts.rows.indexOf(row).takeIf { position -> position >= 0 }
    }
}

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
 *     [FeedViewModel.clearPostToShow]. Null if none was asked for, or [listedPosts] don't hold it:
 *     while the feed shows the posts it had before that one was shared, or once its author isn't
 *     among the friends shown, until the feed shows everyone's posts in their place.
 * @property likedPostIds The IDs of the posts the user liked, whose hearts are filled.
 * @property bookmarkedPostIds The IDs of the posts the user saved, whose bookmarks are filled: none
 *     until they're read, since the posts don't wait for them.
 * @property isLoggedIn Whether a user is logged in, not a guest: the feed lets them like and save
 *     posts, and opens Log in for guests who try.
 * @property authorPhotoUrls The profile photo URL of each user who has one, by user ID, shown on
 *     their posts: none until profiles are read, since the posts don't wait for them, so authors
 *     show their initials meanwhile.
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
    val isLoggedIn: Boolean = false,
    val authorPhotoUrls: Map<String, String> = emptyMap()
) {
    /** Every post, newest first, of which the feed shows [listedPosts]. */
    val posts: PostTable
        get() = listedPosts.table
}

/**
 * Holds posts for [FeedScreen], as the user chooses to see them, the user's likes and bookmarks of
 * them, and deletes the logged-in user's own posts.
 *
 * @param postRepository Where posts are read from.
 * @param postLiker Which posts the user liked, and saves the likes [toggleLike] makes.
 * @param postBookmarker Which posts the user saved, and saves the bookmarks [toggleBookmark] makes.
 * @param postDeleter Deletes the logged-in user's posts and their photos.
 * @param accountRepository Who is logged in: guests can't like or save posts, and only the user's
 *     own posts show a delete button.
 * @param followRepository Who the user's friends are, for [FeedChoice.FRIENDS].
 * @param profileRepository The authors' profiles, whose photos their posts show.
 * @param showInFeedRequest The post another screen asks the feed to show, which it scrolls to,
 *     showing everyone's posts if it shows only friends and the post's author isn't one of them.
 * @param postLookup Tells the feed whether a post it was asked to show, but can't find, was
 *     deleted, so that it withdraws the request.
 * @param showOnMapRequest Where [showOnMap] asks Map to show a post.
 * @param postsDispatcher Where posts are put in order and the post to show is found: off the main
 *     thread, since each is a pass over the posts.
 * @param clock The time posts are aged by for [FeedOrder.BEST].
 * @param sharing When [uiState] follows the posts: while Feed collects it, in the app.
 */
class FeedViewModel(
    postRepository: PostRepository,
    private val postLiker: PostLiker,
    private val postBookmarker: PostBookmarker,
    private val postDeleter: PostDeleter,
    accountRepository: AccountRepository,
    followRepository: FollowRepository,
    profileRepository: ProfileRepository,
    private val showInFeedRequest: ShowPostRequest,
    private val postLookup: PostLookup,
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

    // The posts listed for what the user chose, and the post asked to show, which is shown among
    // everyone's posts or withdrawn when the feed doesn't list it.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val listedFeed: Flow<ListedFeed> =
        combine(rankedPosts, shown, showInFeedRequest.postId) { ranked, shown, postToShow ->
            ListedFeed(shown, shown.authors.listedIn(ranked.listedPosts), postToShow)
        }
            .transformLatest { feed ->
                emit(feed)
                showUnlistedPost(feed)
            }

    /**
     * The current state of the feed, updated whenever posts change, the user picks what to see,
     * their friends change, another screen asks the feed to show a post, or the user likes or saves
     * a post or logs in or out.
     */
    val uiState: StateFlow<FeedUiState> =
        combine(
            listedFeed,
            postLiker.likedPostIds(),
            accountRepository.loggedInUid
        ) { feed, likedPostIds, loggedInUid ->
            FeedUiState(
                listedPosts = feed.listedPosts,
                choice = feed.shown.choice,
                offeredChoices = FeedChoice.offeredTo(isLoggedIn = loggedInUid != null),
                authors = feed.shown.authors,
                isLoading = false,
                positionToShow = feed.positionToShow,
                likedPostIds = likedPostIds,
                isLoggedIn = loggedInUid != null
            )
        }
            .combine(postBookmarker.bookmarkedPostIdsStartingEmpty()) { state, bookmarkedPostIds ->
                state.copy(bookmarkedPostIds = bookmarkedPostIds)
            }
            .combine(authorPhotoUrlsStartingEmpty(profileRepository)) { state, authorPhotoUrls ->
                state.copy(authorPhotoUrls = authorPhotoUrls)
            }
            .flowOn(postsDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = sharing,
                initialValue = FeedUiState()
            )

    /** The logged-in user's ID, or null for guests, who can't delete posts. */
    val loggedInUid: StateFlow<String?> = accountRepository.loggedInUid

    // The photo URL of every user who has one, by ID, starting with none so the posts don't wait
    // for profiles. The repository reads profiles again when its listener fails.
    private fun authorPhotoUrlsStartingEmpty(
        profileRepository: ProfileRepository
    ): Flow<Map<String, String>> = profileRepository.getProfiles()
        .map(::photoUrlsOf)
        .onStart { emit(emptyMap()) }

    private fun photoUrlsOf(profiles: List<UserProfile>): Map<String, String> =
        profiles.filter { profile -> profile.photoUrl.isNotBlank() }
            .associate { profile -> profile.uid to profile.photoUrl }

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

    // Shows the post asked for when the feed doesn't list it: among everyone's posts if the
    // choice leaves its author out, or not at all if it was deleted, withdrawing the request, so
    // that it can't scroll the feed later, when the user picks another choice.
    private suspend fun showUnlistedPost(feed: ListedFeed) {
        val postId = feed.unlistedPostToShow ?: return
        val posts = feed.listedPosts.table
        val row = posts.rowOf(postId)
        if (row == null) {
            withdrawIfDeleted(postId)
        } else if (feed.shown.authors.leavesOut(posts.authorUid(row))) {
            choice.choose(feed.shown.choice.forEveryone())
        }
    }

    // Withdraws the request for the post with [postId], which the feed's table doesn't hold, if
    // the server doesn't hold it either: it was deleted. The table may come from Firestore's cache,
    // from before the post was shared, so only the server can tell. While the server can't be
    // reached, the request waits for the post.
    private suspend fun withdrawIfDeleted(postId: String) {
        val isKnownDeleted = try {
            !postLookup.holdsPost(postId)
        } catch (error: PostLookupException) {
            false
        }
        if (isKnownDeleted) {
            showInFeedRequest.withdraw(postId)
        }
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
        val likedPostIds = uiState.value.likedPostIds
        viewModelScope.launch { postLiker.toggle(postId, likedPostIds) }
    }

    /**
     * Saves the post with [postId], or unsaves it if the user saved it, unless a change to its
     * bookmark is still being saved. A change that can't be saved is logged; the bookmark then
     * shows the post as it was.
     */
    fun toggleBookmark(postId: String) {
        val bookmarkedPostIds = uiState.value.bookmarkedPostIds
        viewModelScope.launch { postBookmarker.toggle(postId, bookmarkedPostIds) }
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
         * Creates a [FeedViewModel] that reads the app's shared posts, likes, bookmarks and
         * requests.
         */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                FeedViewModel(
                    postRepository = application.container.postRepository,
                    postLiker = PostLiker(application.container.likeRepository),
                    postBookmarker = PostBookmarker(application.container.bookmarkRepository),
                    postDeleter = application.container.postDeleter,
                    accountRepository = application.container.accountRepository,
                    followRepository = application.container.followRepository,
                    profileRepository = application.container.profileRepository,
                    showInFeedRequest = application.container.showInFeedRequest,
                    postLookup = application.container.postLookup,
                    showOnMapRequest = application.container.showOnMapRequest,
                    postsDispatcher = Dispatchers.Default,
                    clock = Clock.systemUTC(),
                    sharing = application.container.whileScreenCollects
                )
            }
        }
    }
}
