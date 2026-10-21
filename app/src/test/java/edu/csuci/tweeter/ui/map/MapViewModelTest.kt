@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.map

import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private const val UNKNOWN_POST_ID = "no-such-post"

class MapViewModelTest {
    private val showOnMapRequest = ShowPostRequest()
    private val showInFeedRequest = ShowPostRequest()

    @Before
    fun useTestMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectPost_opensPopupAndMovesToPin() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS[1]

        viewModel.selectPost(post.id)

        assertEquals(
            MapUiState(
                posts = SAMPLE_POSTS,
                selectedPost = post,
                pendingMove = MapMove.ToPost(post.id)
            ),
            viewModel.uiState.value
        )
    }

    @Test
    fun selectPost_unknownPost_opensNoPopup() = runTest {
        val viewModel = mapViewModel()

        viewModel.selectPost(UNKNOWN_POST_ID)

        assertNull(viewModel.uiState.value.selectedPost)
    }

    @Test
    fun selectPost_postNotLoadedYet_opensPopupOnceItLoads() = runTest {
        val repository = FakePostRepository(initialPosts = emptyList())
        val viewModel = mapViewModel(repository)
        val post = SAMPLE_POSTS.first()
        viewModel.selectPost(post.id)

        repository.addPost(post)

        assertEquals(post, viewModel.uiState.value.selectedPost)
    }

    @Test
    fun clearSelection_closesPopupAndDropsMoveToIt() = runTest {
        val viewModel = mapViewModel()
        viewModel.selectPost(SAMPLE_POSTS.first().id)

        viewModel.clearSelection()

        assertEquals(MapUiState(posts = SAMPLE_POSTS), viewModel.uiState.value)
    }

    @Test
    fun clearSelection_keepsMoveToUser() = runTest {
        val viewModel = mapViewModel()
        viewModel.moveToUser()

        viewModel.clearSelection()

        assertEquals(MapMove.ToUser, viewModel.uiState.value.pendingMove)
    }

    @Test
    fun finishMove_keepsPopupOpenWithoutMovingAgain() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS.first()
        viewModel.selectPost(post.id)

        viewModel.finishMove(MapMove.ToPost(post.id))

        assertEquals(
            MapUiState(posts = SAMPLE_POSTS, selectedPost = post, pendingMove = null),
            viewModel.uiState.value
        )
    }

    @Test
    fun finishMove_toEarlierPost_keepsMoveToPostSelectedSince() = runTest {
        val viewModel = mapViewModel()
        val earlier = SAMPLE_POSTS[0]
        val later = SAMPLE_POSTS[1]
        viewModel.selectPost(earlier.id)
        viewModel.selectPost(later.id)

        viewModel.finishMove(MapMove.ToPost(earlier.id))

        assertEquals(MapMove.ToPost(later.id), viewModel.uiState.value.pendingMove)
    }

    @Test
    fun moveToUser_replacesMoveToPost() = runTest {
        val viewModel = mapViewModel()
        viewModel.selectPost(SAMPLE_POSTS.first().id)

        viewModel.moveToUser()

        assertEquals(MapMove.ToUser, viewModel.uiState.value.pendingMove)
    }

    @Test
    fun selectPost_whileWaitingForUsersLocation_movesToPostInstead() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS.first()
        viewModel.moveToUser()

        viewModel.selectPost(post.id)

        assertEquals(MapMove.ToPost(post.id), viewModel.uiState.value.pendingMove)
    }

    @Test
    fun showOnMapRequest_sentBeforeMapOpens_opensPostsPopup() = runTest {
        val post = SAMPLE_POSTS[1]
        showOnMapRequest.send(post.id)

        val viewModel = mapViewModel()

        assertEquals(
            MapUiState(
                posts = SAMPLE_POSTS,
                selectedPost = post,
                pendingMove = MapMove.ToPost(post.id)
            ),
            viewModel.uiState.value
        )
    }

    @Test
    fun showOnMapRequest_sentWhileMapIsOpen_opensPostsPopup() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS[1]

        showOnMapRequest.send(post.id)

        assertEquals(post, viewModel.uiState.value.selectedPost)
    }

    @Test
    fun showOnMapRequest_isHandledOnlyOnce() = runTest {
        showOnMapRequest.send(SAMPLE_POSTS[1].id)
        mapViewModel()

        val reopenedMap = mapViewModel()

        assertEquals(MapUiState(posts = SAMPLE_POSTS), reopenedMap.uiState.value)
    }

    @Test
    fun showInFeed_asksFeedToShowPost() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS[1]

        viewModel.showInFeed(post)

        assertEquals(post.id, showInFeedRequest.postId.value)
    }

    // The map follows its posts only while something collects its state, as MapScreen does. No
    // test shows the map, which needs MapLibre's native engine.
    private fun TestScope.mapViewModel(
        repository: PostRepository = FakePostRepository()
    ): MapViewModel {
        val viewModel = MapViewModel(
            postRepository = repository,
            showOnMapRequest = showOnMapRequest,
            showInFeedRequest = showInFeedRequest,
            savedStateHandle = SavedStateHandle(),
            createMap = { error("Unit tests don't show the map") }
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        return viewModel
    }
}
