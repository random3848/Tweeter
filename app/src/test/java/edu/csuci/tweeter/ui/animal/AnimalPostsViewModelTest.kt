@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.animal

import edu.csuci.tweeter.data.FakePostRepository
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val ISLAND_FOX = "Island fox"

class AnimalPostsViewModelTest {
    private val showOnMapRequest = ShowPostRequest()
    private val postRepository = FakePostRepository(
        initialPosts = listOf(
            SAMPLE_POSTS[0].copy(id = "newer fox", animalType = ISLAND_FOX),
            SAMPLE_POSTS[1].copy(id = "coyote", animalType = "Coyote"),
            SAMPLE_POSTS[2].copy(id = "older fox", animalType = "island fox")
        )
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
    fun uiState_listsRowsOfPostsNamingAnimalNewestFirst() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        assertArrayEquals(intArrayOf(0, 2), viewModel.uiState.value.listedPosts.rows)
    }

    @Test
    fun uiState_postOfAnimalAdded_listsItFirst() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        postRepository.addPost(SAMPLE_POSTS[0].copy(id = "newest fox", animalType = ISLAND_FOX))

        val listedPosts = viewModel.uiState.value.listedPosts
        assertEquals("newest fox", listedPosts.table.id(listedPosts.rows.first()))
    }

    @Test
    fun showOnMap_asksMapToShowPost() = runTest {
        val viewModel = collectedAnimalPostsViewModel()

        viewModel.showOnMap("newer fox")

        assertEquals("newer fox", showOnMapRequest.postId.value)
    }

    private fun TestScope.collectedAnimalPostsViewModel(): AnimalPostsViewModel {
        val viewModel = AnimalPostsViewModel(
            animal = ISLAND_FOX,
            postRepository = postRepository,
            showOnMapRequest = showOnMapRequest,
            rowsDispatcher = UnconfinedTestDispatcher(testScheduler),
            sharing = SharingStarted.WhileSubscribed()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        return viewModel
    }
}
