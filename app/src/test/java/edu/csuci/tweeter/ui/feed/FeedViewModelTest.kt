@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.ui.navigation.ShowPostRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

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
    fun postToShow_isPostMapAskedFeedToShow() {
        val viewModel = feedViewModel()
        val post = SAMPLE_POSTS[1]

        showInFeedRequest.send(post.id)

        assertEquals(post.id, viewModel.postToShow.value)
    }

    @Test
    fun clearPostToShow_forgetsRequest() {
        val viewModel = feedViewModel()
        showInFeedRequest.send(SAMPLE_POSTS[1].id)

        viewModel.clearPostToShow()

        assertNull(viewModel.postToShow.value)
    }

    @Test
    fun showOnMap_asksMapToShowPost() {
        val viewModel = feedViewModel()
        val post = SAMPLE_POSTS[1]

        viewModel.showOnMap(post)

        assertEquals(post.id, showOnMapRequest.postId.value)
    }

    @Test
    fun showOnMap_leavesFeedsOwnRequestAlone() {
        val viewModel = feedViewModel()
        showInFeedRequest.send(SAMPLE_POSTS[0].id)

        viewModel.showOnMap(SAMPLE_POSTS[1])

        assertEquals(SAMPLE_POSTS[0].id, viewModel.postToShow.value)
    }

    private fun feedViewModel() = FeedViewModel(
        postRepository = FakePostRepository(),
        showInFeedRequest = showInFeedRequest,
        showOnMapRequest = showOnMapRequest
    )
}
