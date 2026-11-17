@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PostDeleter
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.data.assertSamePosts
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import java.time.Instant
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

class FeedViewModelTest {
    private val showInFeedRequest = ShowPostRequest()
    private val showOnMapRequest = ShowPostRequest()

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
        postsDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
        loggedInUid: String? = null
    ): FeedViewModel {
        val accountRepository = FakeAccountRepository(uid = loggedInUid)
        return FeedViewModel(
            postRepository = repository,
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
            sharing = SharingStarted.WhileSubscribed()
        )
    }

    // The feed's state follows the repository while the test collects it, as it does in the app
    // while Feed collects it.
    private fun TestScope.collectedFeedViewModel(
        repository: PostRepository = FakePostRepository(),
        loggedInUid: String? = null
    ): FeedViewModel {
        val viewModel =
            feedViewModel(repository, UnconfinedTestDispatcher(testScheduler), loggedInUid)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        return viewModel
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

    private class FakeAccountRepository(uid: String?) : AccountRepository {
        override val loggedInUid = MutableStateFlow(uid)

        override suspend fun logIn(email: String, password: String) = Unit

        override suspend fun createAccount(email: String, password: String) = Unit

        override suspend fun logOut() = Unit

        override suspend fun resetPassword(email: String) = Unit
    }
}
