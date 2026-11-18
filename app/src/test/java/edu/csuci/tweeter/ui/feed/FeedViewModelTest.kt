@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeLikeRepository
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.LikeException
import edu.csuci.tweeter.data.LikeRepository
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostDeleter
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.data.assertSamePosts
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
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
        viewModel.selectOrder(FeedOrder.TOP)

        showInFeedRequest.send(NEW_UNLIKED_ID)

        assertEquals(2, viewModel.uiState.value.positionToShow)
    }

    @Test
    fun uiState_byDefault_ordersBest() = runTest {
        val viewModel = collectedFeedViewModel()

        assertEquals(FeedOrder.BEST, viewModel.uiState.value.order)
    }

    @Test
    fun uiState_best_ranksRecentLikesAboveOldLikesAndNoLikes() = runTest {
        val viewModel = collectedFeedViewModel(FakePostRepository(rankedPosts))

        assertEquals(
            listOf(RECENT_LIKED_ID, OLD_POPULAR_ID, NEW_UNLIKED_ID),
            viewModel.uiState.value.shownPostIds()
        )
    }

    @Test
    fun selectOrder_newest_showsNewestFirst() = runTest {
        val viewModel = collectedFeedViewModel(FakePostRepository(rankedPosts))

        viewModel.selectOrder(FeedOrder.NEWEST)

        assertEquals(
            listOf(NEW_UNLIKED_ID, RECENT_LIKED_ID, OLD_POPULAR_ID),
            viewModel.uiState.value.shownPostIds()
        )
    }

    @Test
    fun selectOrder_top_showsMostLikedFirst() = runTest {
        val viewModel = collectedFeedViewModel(FakePostRepository(rankedPosts))

        viewModel.selectOrder(FeedOrder.TOP)

        assertEquals(
            listOf(OLD_POPULAR_ID, RECENT_LIKED_ID, NEW_UNLIKED_ID),
            viewModel.uiState.value.shownPostIds()
        )
    }

    @Test
    fun uiState_postLikedAfterOrdering_staysWhereItWas() = runTest {
        val repository = LikedPostRepository(rankedPosts)
        val viewModel = collectedFeedViewModel(repository)
        viewModel.selectOrder(FeedOrder.TOP)

        repository.setLikeCount(NEW_UNLIKED_ID, likeCount = 100)

        assertEquals(
            listOf(OLD_POPULAR_ID, RECENT_LIKED_ID, NEW_UNLIKED_ID),
            viewModel.uiState.value.shownPostIds()
        )
    }

    @Test
    fun selectOrder_again_reordersByCurrentLikes() = runTest {
        val repository = LikedPostRepository(rankedPosts)
        val viewModel = collectedFeedViewModel(repository)
        viewModel.selectOrder(FeedOrder.TOP)
        repository.setLikeCount(NEW_UNLIKED_ID, likeCount = 100)

        viewModel.selectOrder(FeedOrder.NEWEST)
        viewModel.selectOrder(FeedOrder.TOP)

        assertEquals(NEW_UNLIKED_ID, viewModel.uiState.value.shownPostIds().first())
    }

    @Test
    fun selectOrder_keepsChosenOrder() = runTest {
        val viewModel = collectedFeedViewModel()

        viewModel.selectOrder(FeedOrder.TOP)

        assertEquals(FeedOrder.TOP, viewModel.uiState.value.order)
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
        val viewModel = collectedFeedViewModel(loggedInUid = USER_UID)

        assertTrue(viewModel.uiState.value.canLike)
    }

    @Test
    fun uiState_guest_cannotLike() = runTest {
        val viewModel = collectedFeedViewModel(loggedInUid = null)

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
        val viewModel = collectedFeedViewModel(repository, loggedInUid = USER_UID)

        viewModel.deletePost(mine.id)

        assertSamePosts(PostTable.of(SAMPLE_POSTS), viewModel.uiState.value.posts)
    }

    private fun feedViewModel(
        repository: PostRepository = FakePostRepository(),
        likes: LikeRepository = FakeLikeRepository(),
        loggedInUid: String? = USER_UID,
        postsDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()
    ): FeedViewModel {
        val accountRepository = FakeAccountRepository(loggedInUid)
        return FeedViewModel(
            postRepository = repository,
            likeRepository = likes,
            postDeleter = PostDeleter(
                postRepository = repository,
                photoRepository = FakePhotoRepository(),
                accountRepository = accountRepository,
                scope = CoroutineScope(postsDispatcher)
            ),
            accountRepository = accountRepository,
            showInFeedRequest = showInFeedRequest,
            showOnMapRequest = showOnMapRequest,
            postsDispatcher = postsDispatcher,
            clock = clock,
            sharing = SharingStarted.WhileSubscribed()
        )
    }

    // The feed's state follows the repository while the test collects it, as it does in the app
    // while Feed collects it.
    private fun TestScope.collectedFeedViewModel(
        repository: PostRepository = FakePostRepository(),
        likes: LikeRepository = FakeLikeRepository(),
        loggedInUid: String? = USER_UID
    ): FeedViewModel {
        val viewModel = feedViewModel(
            repository,
            likes,
            loggedInUid,
            postsDispatcher = UnconfinedTestDispatcher(testScheduler)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        return viewModel
    }

    private fun postShared(id: String, ago: Duration, likeCount: Int) = SAMPLE_POSTS[0].copy(
        id = id,
        timestamp = clock.instant().minus(ago),
        likeCount = likeCount
    )

    private fun FeedUiState.shownPostIds(): List<String> = rows.map(posts::id)

    // Likes that can't be saved, as when the Firestore rules refuse them.
    private class UnsavableLikeRepository : LikeRepository by FakeLikeRepository() {
        override suspend fun like(postId: String): Unit =
            throw LikeException(IllegalStateException("Refused"))
    }

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
