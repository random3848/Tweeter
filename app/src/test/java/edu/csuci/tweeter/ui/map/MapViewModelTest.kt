@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.map

import androidx.lifecycle.SavedStateHandle
import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
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
import org.maplibre.compose.sources.GeoJsonData

private const val UNKNOWN_POST_ID = "no-such-post"

private const val ONLY_ROW = 0

class MapViewModelTest {
    private val showOnMapRequest = ShowPostRequest()
    private val showInFeedRequest = ShowPostRequest()
    private val samplePosts = PinnedPosts(PostTable.of(SAMPLE_POSTS))

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
                pinnedPosts = samplePosts,
                selectedRow = SAMPLE_POSTS.indexOf(post),
                pendingMove = MapMove.ToPost(post.id)
            ),
            viewModel.uiState.value
        )
    }

    @Test
    fun selectPost_unknownPost_opensNoPopup() = runTest {
        val viewModel = mapViewModel()

        viewModel.selectPost(UNKNOWN_POST_ID)

        assertNull(viewModel.uiState.value.selectedRow)
    }

    @Test
    fun selectPost_postNotLoadedYet_opensPopupOnceItLoads() = runTest {
        val repository = FakePostRepository(initialPosts = emptyList())
        val viewModel = mapViewModel(repository)
        val post = SAMPLE_POSTS.first()
        viewModel.selectPost(post.id)

        repository.addPost(post)

        assertEquals(ONLY_ROW, viewModel.uiState.value.selectedRow)
    }

    @Test
    fun clearSelection_closesPopupAndDropsMoveToIt() = runTest {
        val viewModel = mapViewModel()
        viewModel.selectPost(SAMPLE_POSTS.first().id)

        viewModel.clearSelection()

        assertEquals(MapUiState(pinnedPosts = samplePosts), viewModel.uiState.value)
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
            MapUiState(
                pinnedPosts = samplePosts,
                selectedRow = SAMPLE_POSTS.indexOf(post),
                pendingMove = null
            ),
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
                pinnedPosts = samplePosts,
                selectedRow = SAMPLE_POSTS.indexOf(post),
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

        assertEquals(SAMPLE_POSTS.indexOf(post), viewModel.uiState.value.selectedRow)
    }

    @Test
    fun showOnMapRequest_isHandledOnlyOnce() = runTest {
        showOnMapRequest.send(SAMPLE_POSTS[1].id)
        mapViewModel()

        val reopenedMap = mapViewModel()

        assertEquals(MapUiState(pinnedPosts = samplePosts), reopenedMap.uiState.value)
    }

    @Test
    fun showInFeed_asksFeedToShowPost() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS[1]

        viewModel.showInFeed(post.id)

        assertEquals(post.id, showInFeedRequest.postId.value)
    }

    @Test
    fun uiState_postAdded_pinsIt() = runTest {
        val repository = FakePostRepository(initialPosts = emptyList())
        val viewModel = mapViewModel(repository)
        val post = SAMPLE_POSTS.first()

        repository.addPost(post)

        assertEquals(
            GeoJsonData.JsonString(pinsJsonOf(PostTable.of(listOf(post)))),
            viewModel.uiState.value.pinnedPosts.pins
        )
    }

    @Test
    fun selectPost_pinsSelectedPostAlone() = runTest {
        val viewModel = mapViewModel()
        val post = SAMPLE_POSTS[1]

        viewModel.selectPost(post.id)

        val state = viewModel.uiState.value
        assertEquals(
            GeoJsonData.JsonString(pinJsonOf(state.posts, SAMPLE_POSTS.indexOf(post))),
            state.selectedPin
        )
    }

    // The map follows its posts while the test collects its state, as it does in the app while the
    // app is on screen. No test shows the map, which needs MapLibre's native engine.
    private fun TestScope.mapViewModel(
        repository: PostRepository = FakePostRepository()
    ): MapViewModel {
        val viewModel = MapViewModel(
            postRepository = repository,
            showOnMapRequest = showOnMapRequest,
            showInFeedRequest = showInFeedRequest,
            savedStateHandle = SavedStateHandle(),
            createMap = { error("Unit tests don't show the map") },
            pinsDispatcher = UnconfinedTestDispatcher(testScheduler),
            sharing = SharingStarted.WhileSubscribed()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        return viewModel
    }
}
