@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.animal

import edu.csuci.tweeter.data.Animal
import edu.csuci.tweeter.data.AnimalLookupException
import edu.csuci.tweeter.data.AnimalRepository
import edu.csuci.tweeter.data.FakeAnimalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val ISLAND_FOX = "Island fox"

class AnimalDetailsViewModelTest {
    @Before
    fun useTestMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_whileReading_isLoading() {
        val repository = ScriptedAnimalRepository(lookup = { awaitCancellation() })

        val viewModel = AnimalDetailsViewModel(ISLAND_FOX, repository)

        assertEquals(AnimalDetailsUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_knownAnimal_isFoundWithIt() {
        val viewModel = AnimalDetailsViewModel(ISLAND_FOX, FakeAnimalRepository(listOf(ISLAND_FOX)))

        assertEquals(
            AnimalDetailsUiState.Found(Animal(name = ISLAND_FOX)),
            viewModel.uiState.value
        )
    }

    @Test
    fun uiState_unknownAnimal_isNotFound() {
        val viewModel = AnimalDetailsViewModel(ISLAND_FOX, FakeAnimalRepository(listOf("Coyote")))

        assertEquals(AnimalDetailsUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun uiState_lookupFails_isFailed() {
        val viewModel = AnimalDetailsViewModel(ISLAND_FOX, unreachableRepository())

        assertEquals(AnimalDetailsUiState.Failed, viewModel.uiState.value)
    }

    @Test
    fun retry_afterFailure_readsTheAnimalAgain() {
        val repository = unreachableRepository()
        val viewModel = AnimalDetailsViewModel(ISLAND_FOX, repository)
        repository.lookup = { name -> Animal(name = name) }

        viewModel.retry()

        assertEquals(
            AnimalDetailsUiState.Found(Animal(name = ISLAND_FOX)),
            viewModel.uiState.value
        )
    }

    @Test
    fun retry_whileReadingAgain_isLoading() {
        val repository = unreachableRepository()
        val viewModel = AnimalDetailsViewModel(ISLAND_FOX, repository)
        repository.lookup = { awaitCancellation() }

        viewModel.retry()

        assertEquals(AnimalDetailsUiState.Loading, viewModel.uiState.value)
    }

    private fun unreachableRepository() = ScriptedAnimalRepository(
        lookup = { name -> throw AnimalLookupException("Reading \"$name\" failed") }
    )

    /** An [AnimalRepository] that answers lookups with [lookup], which a test may change. */
    private class ScriptedAnimalRepository(var lookup: suspend (String) -> Animal?) :
        AnimalRepository {
        override suspend fun searchAnimals(query: String): List<String> = emptyList()

        override suspend fun getAnimal(name: String): Animal? = lookup(name)
    }
}
