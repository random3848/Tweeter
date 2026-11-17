@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.search

import edu.csuci.tweeter.data.FakePostRepository
import edu.csuci.tweeter.data.FakeProfileRepository
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.data.UserProfile
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchViewModelTest {
    private val maya = profileNamed(uid = "maya", displayName = "Maya K")
    private val jordan = profileNamed(uid = "jordan", displayName = "Jordan R")
    private val profileRepository = FakeProfileRepository(listOf(maya, jordan))
    private val postRepository = FakePostRepository(
        initialPosts = listOf(
            SAMPLE_POSTS[0].copy(id = "fox", animalType = "Island fox"),
            SAMPLE_POSTS[1].copy(id = "coyote", animalType = "Coyote")
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
    fun updateQuery_findsPeopleByName() = runTest {
        val viewModel = collectedSearchViewModel()

        viewModel.updateQuery("may")

        assertEquals(listOf(maya), viewModel.results.value.people)
    }

    @Test
    fun updateQuery_findsAnimalsThatPostsName() = runTest {
        val viewModel = collectedSearchViewModel()

        viewModel.updateQuery("fox")

        assertEquals(
            listOf(PostedAnimal("Island fox", postCount = 1)),
            viewModel.results.value.animals
        )
    }

    @Test
    fun updateQuery_blank_findsNothing() = runTest {
        val viewModel = collectedSearchViewModel()
        viewModel.updateQuery("o")

        viewModel.updateQuery(" ")

        val results = viewModel.results.value
        assertEquals(emptyList<Any>(), results.people + results.animals)
    }

    @Test
    fun results_profileRenamed_findsNewName() = runTest {
        val viewModel = collectedSearchViewModel()
        viewModel.updateQuery("sam")

        profileRepository.saveProfile(maya.copy(displayName = "Samantha"))

        assertEquals(listOf("Samantha"), viewModel.results.value.people.map { it.displayName })
    }

    @Test
    fun results_beforeProfilesArrive_areLoadingButFindAnimals() = runTest {
        val viewModel = collectedSearchViewModel(ProfilelessProfileRepository())

        viewModel.updateQuery("coyote")

        val results = viewModel.results.value
        assertTrue(results.isLoadingPeople)
        assertEquals(listOf("Coyote"), results.animals.map { it.name })
    }

    // Searches while the test collects its results, as Search does while it's shown.
    private fun TestScope.collectedSearchViewModel(
        profiles: ProfileRepository = profileRepository
    ): SearchViewModel {
        val viewModel = SearchViewModel(
            profileRepository = profiles,
            postRepository = postRepository,
            searchDispatcher = UnconfinedTestDispatcher(testScheduler),
            sharing = SharingStarted.WhileSubscribed()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.results.collect()
        }
        return viewModel
    }

    private fun profileNamed(uid: String, displayName: String): UserProfile =
        UserProfile.createDefault(uid).copy(displayName = displayName)

    // A repository whose profiles never arrive, like a listener the server hasn't answered yet.
    private class ProfilelessProfileRepository : ProfileRepository by FakeProfileRepository() {
        override fun getProfiles(): Flow<List<UserProfile>> = flow { awaitCancellation() }
    }
}
