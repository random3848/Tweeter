@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.FakeAccountRepository
import edu.csuci.tweeter.data.FakeLikeRepository
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.LikeException
import edu.csuci.tweeter.data.LikeRepository
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.data.assertSamePosts
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
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
private const val MAYA = "maya"

class FeedViewModelTest {
    private val showInFeedRequest = ShowPostRequest()
    private val showOnMapRequest = ShowPostRequest()
    private val likedPostId = SAMPLE_POSTS[0].id
    private val unlikedPostId = SAMPLE_POSTS[1].id

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
    fun uiState_postAskedToShow_hasItsRow() = runTest {
        val viewModel = collectedFeedViewModel()
        val post = SAMPLE_POSTS[1]

        showInFeedRequest.send(post.id)

        assertEquals(SAMPLE_POSTS.indexOf(post), viewModel.uiState.value.rowToShow)
    }

    @Test
    fun uiState_postAskedToShowNotLoadedYet_hasItsRowOnceItArrives() = runTest {
        val repository = FakePostRepository()
        val viewModel = collectedFeedViewModel(repository)
        showInFeedRequest.send(NEW_POST_ID)
        val rowBeforePostArrives = viewModel.uiState.value.rowToShow

        repository.addPost(SAMPLE_POSTS.first().copy(id = NEW_POST_ID, timestamp = Instant.now()))

        assertNull(rowBeforePostArrives)
        assertEquals(0, viewModel.uiState.value.rowToShow)
    }

    @Test
    fun clearPostToShow_leavesNoRowToShow() = runTest {
        val viewModel = collectedFeedViewModel()
        showInFeedRequest.send(SAMPLE_POSTS[1].id)

        viewModel.clearPostToShow()

        assertNull(viewModel.uiState.value.rowToShow)
    }

    @Test
    fun uiState_holdsPostsUserLiked() = runTest {
        val viewModel = collectedFeedViewModel(likes = FakeLikeRepository(setOf(likedPostId)))

        assertEquals(setOf(likedPostId), viewModel.uiState.value.likedPostIds)
    }

    @Test
    fun uiState_userLoggedIn_canLike() = runTest {
        val viewModel = collectedFeedViewModel(account = FakeAccountRepository(MAYA))

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

    private fun feedViewModel(
        repository: PostRepository = FakePostRepository(),
        likes: LikeRepository = FakeLikeRepository(),
        account: AccountRepository = FakeAccountRepository(MAYA),
        postsDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()
    ) = FeedViewModel(
        postRepository = repository,
        likeRepository = likes,
        accountRepository = account,
        showInFeedRequest = showInFeedRequest,
        showOnMapRequest = showOnMapRequest,
        postsDispatcher = postsDispatcher,
        sharing = SharingStarted.WhileSubscribed()
    )

    // The feed's state follows the repository while the test collects it, as it does in the app
    // while Feed collects it.
    private fun TestScope.collectedFeedViewModel(
        repository: PostRepository = FakePostRepository(),
        likes: LikeRepository = FakeLikeRepository(),
        account: AccountRepository = FakeAccountRepository(MAYA)
    ): FeedViewModel {
        val viewModel = feedViewModel(
            repository,
            likes,
            account,
            postsDispatcher = UnconfinedTestDispatcher(testScheduler)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        return viewModel
    }

    // Likes that can't be saved, as when the Firestore rules refuse them.
    private class UnsavableLikeRepository : LikeRepository by FakeLikeRepository() {
        override suspend fun like(postId: String): Unit =
            throw LikeException(IllegalStateException("Refused"))
    }

    // A repository whose first table never arrives, as while Firestore is still answering.
    private class TablelessPostRepository : PostRepository by FakePostRepository() {
        override fun getPostTable(): Flow<PostTable> = flow { awaitCancellation() }
    }
}
