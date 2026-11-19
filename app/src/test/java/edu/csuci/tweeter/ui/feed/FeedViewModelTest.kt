@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.AnimalVote
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeFollowRepository
import edu.csuci.tweeter.data.FakeLikeRepository
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.FakeVoteRepository
import edu.csuci.tweeter.data.FollowRepository
import edu.csuci.tweeter.data.LikeRepository
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostBallot
import edu.csuci.tweeter.data.PostDeleter
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.data.UnsavableLikeRepository
import edu.csuci.tweeter.data.VoteRepository
import edu.csuci.tweeter.data.assertSamePosts
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import edu.csuci.tweeter.ui.voting.PostVoter
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val NEW_POST_ID = "new"
private const val USER_UID = "maya"
private const val NEW_UNLIKED_ID = "new-unliked"
private const val RECENT_LIKED_ID = "recent-liked"
private const val OLD_POPULAR_ID = "old-popular"
private const val FRIEND_UID = "jordan"
private const val FOLLOWED_UID = "sam"
private const val FRIENDS_NEWER_POST_ID = "friends-newer"
private const val FRIENDS_OLDER_POST_ID = "friends-older"
private const val FOLLOWED_POST_ID = "followed"
private const val POSSUM = "Possum"
private const val VOTE_ROUND = 0

class FeedViewModelTest {
    private val showInFeedRequest = ShowPostRequest()
    private val showOnMapRequest = ShowPostRequest()
    private val likedPostId = SAMPLE_POSTS[0].id
    private val unlikedPostId = SAMPLE_POSTS[1].id
    private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)

    // Newest first, as the repository gives them: each order puts them differently.
    private val rankedPosts = listOf(
        postShared(NEW_UNLIKED_ID, Duration.ofMinutes(10), likeCount = 0),
        postShared(RECENT_LIKED_ID, Duration.ofHours(1), likeCount = 3),
        postShared(OLD_POPULAR_ID, Duration.ofDays(2), likeCount = 40)
    )

    @Before
    fun useTestMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_beforeFirstTable_isLoading() = runTest {
        val viewModel = collectedFeedViewModel(TablelessPostRepository())

        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun uiState_afterFirstTable_holdsRepositoryTable() = runTest {
        val viewModel = collectedFeedViewModel()

        val uiState = viewModel.uiState.value

        assertFalse(uiState.isLoading)
        assertSamePosts(PostTable.of(SAMPLE_POSTS), uiState.posts)
    }

    @Test
    fun uiState_afterFirstTable_listsEveryRowOnce() = runTest {
        val viewModel = collectedFeedViewModel()

        val rows = viewModel.uiState.value.listedPosts.rows

        assertArrayEquals(SAMPLE_POSTS.indices.toList().toIntArray(), rows.sortedArray())
    }

    @Test
    fun uiState_afterAddPost_hasNewPostFirst() = runTest {
        val repository = FakePostRepository()
        val viewModel = collectedFeedViewModel(repository)
        val post = SAMPLE_POSTS.first().copy(id = NEW_POST_ID, timestamp = Instant.now())

        repository.addPost(post)

        assertEquals(NEW_POST_ID, viewModel.uiState.value.posts.id(0))
    }

    @Test
    fun uiState_postAskedToShow_hasItsPosition() = runTest {
        val viewModel = collectedFeedViewModel()
        val post = SAMPLE_POSTS[1]

        showInFeedRequest.send(post.id)

        assertEquals(SAMPLE_POSTS.indexOf(post), viewModel.uiState.value.positionToShow)
    }

    @Test
    fun uiState_postAskedToShowNotLoadedYet_hasItsPositionOnceItArrives() = runTest {
        val repository = FakePostRepository()
        val viewModel = collectedFeedViewModel(repository)
        showInFeedRequest.send(NEW_POST_ID)
        val positionBeforePostArrives = viewModel.uiState.value.positionToShow

        repository.addPost(SAMPLE_POSTS.first().copy(id = NEW_POST_ID, timestamp = Instant.now()))

        assertNull(positionBeforePostArrives)
        assertEquals(0, viewModel.uiState.value.positionToShow)
    }

    @Test
    fun uiState_postAskedToShowInTopOrder_hasItsPositionInThatOrder() = runTest {
        val viewModel = collectedFeedViewModel(FakePostRepository(rankedPosts))
        viewModel.choose(FeedChoice.TOP)

        showInFeedRequest.send(NEW_UNLIKED_ID)

        assertEquals(2, viewModel.uiState.value.positionToShow)
    }

    @Test
    fun uiState_byDefault_choosesBest() = runTest {
        val viewModel = collectedFeedViewModel()

        assertEquals(FeedChoice.BEST, viewModel.uiState.value.choice)
    }

    @Test
    fun uiState_best_ranksRecentLikesFirstAndNewPostsAboveOldLikes() = runTest {
        val viewModel = collectedFeedViewModel(FakePostRepository(rankedPosts))

        assertEquals(
            listOf(RECENT_LIKED_ID, NEW_UNLIKED_ID, OLD_POPULAR_ID),
            viewModel.uiState.value.shownPostIds()
        )
    }

    @Test
    fun choose_newest_showsNewestFirst() = runTest {
        val viewModel = collectedFeedViewModel(FakePostRepository(rankedPosts))

        viewModel.choose(FeedChoice.NEWEST)

        assertEquals(
            listOf(NEW_UNLIKED_ID, RECENT_LIKED_ID, OLD_POPULAR_ID),
            viewModel.uiState.value.shownPostIds()
        )
    }

    @Test
    fun choose_top_showsMostLikedFirst() = runTest {
        val viewModel = collectedFeedViewModel(FakePostRepository(rankedPosts))

        viewModel.choose(FeedChoice.TOP)

        assertEquals(
            listOf(OLD_POPULAR_ID, RECENT_LIKED_ID, NEW_UNLIKED_ID),
            viewModel.uiState.value.shownPostIds()
        )
    }

    @Test
    fun uiState_postLikedAfterOrdering_staysWhereItWas() = runTest {
        val repository = LikedPostRepository(rankedPosts)
        val viewModel = collectedFeedViewModel(repository)
        viewModel.choose(FeedChoice.TOP)

        repository.setLikeCount(NEW_UNLIKED_ID, likeCount = 100)

        assertEquals(
            listOf(OLD_POPULAR_ID, RECENT_LIKED_ID, NEW_UNLIKED_ID),
            viewModel.uiState.value.shownPostIds()
        )
    }

    @Test
    fun choose_again_reordersByCurrentLikes() = runTest {
        val repository = LikedPostRepository(rankedPosts)
        val viewModel = collectedFeedViewModel(repository)
        viewModel.choose(FeedChoice.TOP)
        repository.setLikeCount(NEW_UNLIKED_ID, likeCount = 100)

        viewModel.choose(FeedChoice.NEWEST)
        viewModel.choose(FeedChoice.TOP)

        assertEquals(NEW_UNLIKED_ID, viewModel.uiState.value.shownPostIds().first())
    }

    @Test
    fun choose_keepsChoice() = runTest {
        val viewModel = collectedFeedViewModel()

        viewModel.choose(FeedChoice.TOP)

        assertEquals(FeedChoice.TOP, viewModel.uiState.value.choice)
    }

    @Test
    fun uiState_loggedIn_offersFriends() = runTest {
        val viewModel = collectedFeedViewModel()

        assertTrue(FeedChoice.FRIENDS in viewModel.uiState.value.offeredChoices)
    }

    @Test
    fun uiState_guest_doesNotOfferFriends() = runTest {
        val viewModel =
            collectedFeedViewModel(account = FakeAccountRepository(initialUid = null))

        assertFalse(FeedChoice.FRIENDS in viewModel.uiState.value.offeredChoices)
    }

    @Test
    fun choose_friends_showsMutualFollowsPostsNewestFirst() = runTest {
        val viewModel = collectedFeedViewModel(
            FakePostRepository(postsByFriendAndOthers),
            follows = followsOfUser()
        )

        viewModel.choose(FeedChoice.FRIENDS)

        assertEquals(
            listOf(FRIENDS_NEWER_POST_ID, FRIENDS_OLDER_POST_ID),
            viewModel.uiState.value.shownPostIds()
        )
    }

    @Test
    fun choose_friendsWhileTheyAreRead_isLoadingFriends() = runTest {
        val follows = followsOfUser()
        follows.readsAnswered = CompletableDeferred()
        val viewModel = collectedFeedViewModel(follows = follows)

        viewModel.choose(FeedChoice.FRIENDS)

        assertEquals(FeedAuthors.LoadingFriends, viewModel.uiState.value.authors)
    }

    @Test
    fun choose_friendsWhoCannotBeRead_isUnknownFriends() = runTest {
        val follows = followsOfUser()
        follows.failsReads = true
        val viewModel = collectedFeedViewModel(follows = follows)

        viewModel.choose(FeedChoice.FRIENDS)

        assertEquals(FeedAuthors.UnknownFriends, viewModel.uiState.value.authors)
    }

    @Test
    fun retryFriends_afterReadFailed_showsFriendsPosts() = runTest {
        val follows = followsOfUser()
        follows.failsReads = true
        val viewModel =
            collectedFeedViewModel(FakePostRepository(postsByFriendAndOthers), follows = follows)
        viewModel.choose(FeedChoice.FRIENDS)

        follows.failsReads = false
        viewModel.retryFriends()

        assertEquals(
            listOf(FRIENDS_NEWER_POST_ID, FRIENDS_OLDER_POST_ID),
            viewModel.uiState.value.shownPostIds()
        )
    }

    @Test
    fun uiState_newFriendOnFriends_showsTheirPosts() = runTest {
        val follows = followsOfUser()
        val viewModel =
            collectedFeedViewModel(FakePostRepository(postsByFriendAndOthers), follows = follows)
        viewModel.choose(FeedChoice.FRIENDS)

        follows.addFollow(FOLLOWED_UID, USER_UID)

        assertEquals(
            listOf(FRIENDS_NEWER_POST_ID, FOLLOWED_POST_ID, FRIENDS_OLDER_POST_ID),
            viewModel.uiState.value.shownPostIds()
        )
    }

    @Test
    fun choose_guestPicksNewest_showsNewestFirst() = runTest {
        val viewModel = collectedFeedViewModel(
            FakePostRepository(rankedPosts),
            account = FakeAccountRepository(initialUid = null)
        )

        viewModel.choose(FeedChoice.NEWEST)

        assertEquals(
            listOf(NEW_UNLIKED_ID, RECENT_LIKED_ID, OLD_POPULAR_ID),
            viewModel.uiState.value.shownPostIds()
        )
    }

    @Test
    fun choose_friends_readsThemOnce() = runTest {
        val follows = followsOfUser()
        val viewModel = collectedFeedViewModel(follows = follows)

        viewModel.choose(FeedChoice.FRIENDS)

        // One listener on the user's followers, and one on whether the user follows back their
        // one follower.
        assertEquals(2, follows.openReadCount)
    }

    @Test
    fun uiState_otherUserLogsInAfterFriendsWereChosen_startsOnBest() = runTest {
        val account = FakeAccountRepository(USER_UID)
        val viewModel = collectedFeedViewModel(account = account, follows = followsOfUser())
        viewModel.choose(FeedChoice.FRIENDS)

        account.logInAs(FRIEND_UID)

        assertEquals(FeedChoice.BEST, viewModel.uiState.value.choice)
    }

    @Test
    fun uiState_logOutOnFriends_showsBestOfEveryone() = runTest {
        val account = FakeAccountRepository(USER_UID)
        val viewModel = collectedFeedViewModel(
            FakePostRepository(postsByFriendAndOthers),
            account = account,
            follows = followsOfUser()
        )
        viewModel.choose(FeedChoice.FRIENDS)

        account.logOut()

        val uiState = viewModel.uiState.value
        assertEquals(FeedChoice.BEST, uiState.choice)
        assertEquals(postsByFriendAndOthers.size, uiState.listedPosts.rows.size)
    }

    @Test
    fun uiState_postAskedToShowNotAmongFriends_hasNoPosition() = runTest {
        val viewModel = collectedFeedViewModel(
            FakePostRepository(postsByFriendAndOthers),
            follows = followsOfUser()
        )
        viewModel.choose(FeedChoice.FRIENDS)

        showInFeedRequest.send(FOLLOWED_POST_ID)

        assertNull(viewModel.uiState.value.positionToShow)
    }

    @Test
    fun clearPostToShow_leavesNoPositionToShow() = runTest {
        val viewModel = collectedFeedViewModel()
        showInFeedRequest.send(SAMPLE_POSTS[1].id)

        viewModel.clearPostToShow()

        assertNull(viewModel.uiState.value.positionToShow)
    }

    @Test
    fun uiState_holdsPostsUserLiked() = runTest {
        val viewModel = collectedFeedViewModel(likes = FakeLikeRepository(setOf(likedPostId)))

        assertEquals(setOf(likedPostId), viewModel.uiState.value.likedPostIds)
    }

    @Test
    fun uiState_userLoggedIn_canLike() = runTest {
        val viewModel = collectedFeedViewModel(account = FakeAccountRepository(USER_UID))

        assertTrue(viewModel.uiState.value.canLike)
    }

    @Test
    fun uiState_guest_cannotLike() = runTest {
        val viewModel = collectedFeedViewModel(account = FakeAccountRepository(initialUid = null))

        assertFalse(viewModel.uiState.value.canLike)
    }

    @Test
    fun toggleLike_postNotLiked_likesIt() = runTest {
        val viewModel = collectedFeedViewModel(likes = FakeLikeRepository(setOf(likedPostId)))

        viewModel.toggleLike(unlikedPostId)

        assertEquals(setOf(likedPostId, unlikedPostId), viewModel.uiState.value.likedPostIds)
    }

    @Test
    fun toggleLike_likedPost_unlikesIt() = runTest {
        val viewModel = collectedFeedViewModel(likes = FakeLikeRepository(setOf(likedPostId)))

        viewModel.toggleLike(likedPostId)

        assertEquals(emptySet<String>(), viewModel.uiState.value.likedPostIds)
    }

    @Test
    fun toggleLike_likeNotSaved_keepsPostUnliked() = runTest {
        val viewModel = collectedFeedViewModel(likes = UnsavableLikeRepository())

        viewModel.toggleLike(unlikedPostId)

        assertEquals(emptySet<String>(), viewModel.uiState.value.likedPostIds)
    }

    @Test
    fun showOnMap_asksMapToShowPost() {
        val viewModel = feedViewModel()
        val post = SAMPLE_POSTS[1]

        viewModel.showOnMap(post.id)

        assertEquals(post.id, showOnMapRequest.postId.value)
    }

    @Test
    fun showOnMap_leavesFeedsOwnRequestAlone() {
        val viewModel = feedViewModel()
        showInFeedRequest.send(SAMPLE_POSTS[0].id)

        viewModel.showOnMap(SAMPLE_POSTS[1].id)

        assertEquals(SAMPLE_POSTS[0].id, showInFeedRequest.postId.value)
    }

    @Test
    fun deletePost_loggedInUsersPost_dropsItFromFeed() = runTest {
        val mine = SAMPLE_POSTS.first().copy(id = NEW_POST_ID, authorUid = USER_UID)
        val repository = FakePostRepository(initialPosts = listOf(mine) + SAMPLE_POSTS)
        val viewModel =
            collectedFeedViewModel(repository, account = FakeAccountRepository(USER_UID))

        viewModel.deletePost(mine.id)

        assertSamePosts(PostTable.of(SAMPLE_POSTS), viewModel.uiState.value.posts)
    }

    @Test
    fun userVotes_holdsUsersVotes() = runTest {
        val vote = AnimalVote(POSSUM, VOTE_ROUND)
        val votes = FakeVoteRepository(initialVotes = mapOf(likedPostId to vote))

        val viewModel = feedViewModelFollowingVotes(votes)

        assertEquals(mapOf(likedPostId to vote), viewModel.userVotes.value)
    }

    @Test
    fun toggleVote_savesUsersVote() = runTest {
        val viewModel = feedViewModelFollowingVotes(FakeVoteRepository())
        val ballot = PostBallot(likedPostId, VOTE_ROUND, userVote = null)

        viewModel.toggleVote(ballot, POSSUM)

        val expected = mapOf(likedPostId to AnimalVote(POSSUM, VOTE_ROUND))
        assertEquals(expected, viewModel.userVotes.value)
    }

    @Test
    fun reclassify_reclassifiesPost() = runTest {
        val votes = FakeVoteRepository()
        val viewModel = feedViewModelFollowingVotes(votes)

        viewModel.reclassify(likedPostId, POSSUM)

        assertEquals(mapOf(likedPostId to POSSUM), votes.reclassifiedAnimals.value)
    }

    private fun feedViewModel(
        repository: PostRepository = FakePostRepository(),
        likes: LikeRepository = FakeLikeRepository(),
        account: AccountRepository = FakeAccountRepository(USER_UID),
        follows: FollowRepository = FakeFollowRepository(),
        postsDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
        votes: VoteRepository = FakeVoteRepository()
    ): FeedViewModel = FeedViewModel(
        postRepository = repository,
        postLiker = PostLiker(likes),
        postVoter = PostVoter(votes),
        postDeleter = PostDeleter(
            postRepository = repository,
            photoRepository = FakePhotoRepository(),
            accountRepository = account,
            scope = CoroutineScope(postsDispatcher)
        ),
        accountRepository = account,
        followRepository = follows,
        showInFeedRequest = showInFeedRequest,
        showOnMapRequest = showOnMapRequest,
        postsDispatcher = postsDispatcher,
        clock = clock,
        sharing = SharingStarted.WhileSubscribed()
    )

    // The feed's state follows the repository while the test collects it, as it does in the app
    // while Feed collects it.
    private fun TestScope.collectedFeedViewModel(
        repository: PostRepository = FakePostRepository(),
        likes: LikeRepository = FakeLikeRepository(),
        account: AccountRepository = FakeAccountRepository(USER_UID),
        follows: FollowRepository = FakeFollowRepository()
    ): FeedViewModel {
        val viewModel = feedViewModel(
            repository,
            likes,
            account,
            follows,
            postsDispatcher = UnconfinedTestDispatcher(testScheduler)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        return viewModel
    }

    // The user's votes follow [votes] while the test collects them, as they do in the app while
    // Feed collects them.
    private fun TestScope.feedViewModelFollowingVotes(votes: VoteRepository): FeedViewModel {
        val viewModel = feedViewModel(
            postsDispatcher = UnconfinedTestDispatcher(testScheduler),
            votes = votes
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.userVotes.collect()
        }
        return viewModel
    }

    // Newest first: the friend's two posts, with a post by someone the user follows who doesn't
    // follow back, and the user's own, between them.
    private val postsByFriendAndOthers = listOf(
        postBy(FRIEND_UID, FRIENDS_NEWER_POST_ID, Duration.ofMinutes(5)),
        postBy(FOLLOWED_UID, FOLLOWED_POST_ID, Duration.ofMinutes(30)),
        postBy(USER_UID, NEW_POST_ID, Duration.ofHours(1)),
        postBy(FRIEND_UID, FRIENDS_OLDER_POST_ID, Duration.ofHours(3))
    )

    // The user and FRIEND_UID follow each other; the user follows FOLLOWED_UID, who doesn't
    // follow back.
    private fun followsOfUser() = FakeFollowRepository().apply {
        addFollow(USER_UID, FRIEND_UID)
        addFollow(FRIEND_UID, USER_UID)
        addFollow(USER_UID, FOLLOWED_UID)
    }

    private fun postBy(authorUid: String, id: String, ago: Duration) =
        postShared(id, ago, likeCount = 0).copy(authorUid = authorUid)

    private fun postShared(id: String, ago: Duration, likeCount: Int) = SAMPLE_POSTS[0].copy(
        id = id,
        timestamp = clock.instant().minus(ago),
        likeCount = likeCount
    )

    private fun FeedUiState.shownPostIds(): List<String> = listedPosts.rows.map(posts::id)

    // Posts whose like counts a test changes, as other users' likes do.
    private class LikedPostRepository(initialPosts: List<Post>) :
        PostRepository by FakePostRepository() {
        private val posts = MutableStateFlow(initialPosts)

        override fun getPostTable(): Flow<PostTable> = posts.map(PostTable::of)

        fun setLikeCount(postId: String, likeCount: Int) {
            posts.update { current ->
                current.map { post ->
                    if (post.id == postId) post.copy(likeCount = likeCount) else post
                }
            }
        }
    }

    // A repository whose first table never arrives, as while Firestore is still answering.
    private class TablelessPostRepository : PostRepository by FakePostRepository() {
        override fun getPostTable(): Flow<PostTable> = flow { awaitCancellation() }
    }

    private class FakePhotoRepository : PhotoRepository {
        override suspend fun uploadPhoto(photoUri: String): String = ""

        override suspend fun uploadProfilePhoto(photoUri: String): String = ""

        override suspend fun deletePhoto(photoUrl: String) = Unit
    }
}
