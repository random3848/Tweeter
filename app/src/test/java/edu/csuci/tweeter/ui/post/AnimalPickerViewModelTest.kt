package edu.csuci.tweeter.ui.post

import edu.csuci.tweeter.data.Animal
import edu.csuci.tweeter.data.AnimalLookupException
import edu.csuci.tweeter.data.AnimalRepository
import edu.csuci.tweeter.data.FakeAnimalRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnimalPickerViewModelTest {
    private val animals = listOf("Red-tailed hawk", "Island fox", "Hawk moth")

    // Runs the searches' waits for typing to pause in virtual time.
    private val scheduler = TestCoroutineScheduler()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_emptyFieldIsComplete() {
        val viewModel = createViewModel()

        assertEquals("", viewModel.query)
        assertEquals(emptyList<String>(), viewModel.suggestions)
        assertTrue(viewModel.isComplete)
    }

    @Test
    fun updateQuery_suggestsMatchingAnimals() {
        val viewModel = createViewModel()

        viewModel.typeQuery("hawk")

        assertEquals(listOf("Hawk moth", "Red-tailed hawk"), viewModel.suggestions)
    }

    @Test
    fun updateQuery_textNotChosenFromSuggestions_isNotComplete() {
        val viewModel = createViewModel()

        viewModel.typeQuery("hawk")

        assertFalse(viewModel.isComplete)
    }

    @Test
    fun updateQuery_blank_isCompleteWithoutSuggestions() {
        val viewModel = createViewModel()
        viewModel.typeQuery("hawk")

        viewModel.typeQuery(" ")

        assertEquals(emptyList<String>(), viewModel.suggestions)
        assertTrue(viewModel.isComplete)
    }

    @Test
    fun chooseAnimal_fillsFieldAndHidesSuggestions() {
        val viewModel = createViewModel()
        viewModel.typeQuery("fox")

        viewModel.chooseAnimal("Island fox")

        assertEquals("Island fox", viewModel.query)
        assertEquals("Island fox", viewModel.chosenAnimal)
        assertEquals(emptyList<String>(), viewModel.suggestions)
        assertTrue(viewModel.isComplete)
    }

    @Test
    fun updateQuery_afterChoosing_forgetsChoice() {
        val viewModel = createViewModel()
        viewModel.chooseAnimal("Island fox")

        viewModel.typeQuery("Island fo")

        assertNull(viewModel.chosenAnimal)
        assertFalse(viewModel.isComplete)
    }

    @Test
    fun clear_emptiesField() {
        val viewModel = createViewModel()
        viewModel.typeQuery("fox")
        viewModel.chooseAnimal("Island fox")

        viewModel.clear()

        assertEquals("", viewModel.query)
        assertNull(viewModel.chosenAnimal)
        assertEquals(emptyList<String>(), viewModel.suggestions)
    }

    @Test
    fun updateQuery_earlierSearchFinishesLast_keepsLatestSuggestions() {
        val repository = SlowAnimalRepository()
        val viewModel = AnimalPickerViewModel(repository)
        viewModel.typeQuery("ha")
        viewModel.typeQuery("hawk")

        repository.finish("hawk", listOf("Red-tailed hawk"))
        repository.finish("ha", listOf("Harbor seal", "Red-tailed hawk"))

        assertEquals(listOf("Red-tailed hawk"), viewModel.suggestions)
    }

    @Test
    fun chooseAnimal_whileSearching_doesNotShowSuggestionsAfterward() {
        val repository = SlowAnimalRepository()
        val viewModel = AnimalPickerViewModel(repository)
        viewModel.typeQuery("fox")

        viewModel.chooseAnimal("Island fox")
        repository.finish("fox", listOf("Island fox"))

        assertEquals(emptyList<String>(), viewModel.suggestions)
    }

    @Test
    fun updateQuery_whileTyping_searchesOnceTypingPauses() {
        val repository = CountingAnimalRepository()
        val viewModel = AnimalPickerViewModel(repository)

        viewModel.updateQuery("f")
        viewModel.updateQuery("fo")
        viewModel.updateQuery("fox")
        scheduler.advanceUntilIdle()

        assertEquals(listOf("fox"), repository.queries)
    }

    @Test
    fun updateQuery_searchFails_reportsFailureWithoutSuggestions() {
        val viewModel = AnimalPickerViewModel(UnreachableAnimalRepository())

        viewModel.typeQuery("fox")

        assertTrue(viewModel.searchFailed)
        assertEquals(emptyList<String>(), viewModel.suggestions)
    }

    @Test
    fun updateQuery_afterFailedSearch_forgetsFailure() {
        val viewModel = AnimalPickerViewModel(UnreachableAnimalRepository())
        viewModel.typeQuery("fox")

        viewModel.updateQuery("foxe")

        assertFalse(viewModel.searchFailed)
    }

    @Test
    fun clear_afterFailedSearch_forgetsFailure() {
        val viewModel = AnimalPickerViewModel(UnreachableAnimalRepository())
        viewModel.typeQuery("fox")

        viewModel.clear()

        assertFalse(viewModel.searchFailed)
    }

    private fun createViewModel() = AnimalPickerViewModel(FakeAnimalRepository(animals))

    // Types [text] into the field, then waits for the search that starts once typing pauses.
    private fun AnimalPickerViewModel.typeQuery(text: String) {
        updateQuery(text)
        scheduler.advanceUntilIdle()
    }

    /** An [AnimalRepository] that records the queries it was asked to search. */
    private class CountingAnimalRepository : AnimalRepository {
        val queries = mutableListOf<String>()

        override suspend fun searchAnimals(query: String): List<String> {
            queries += query
            return emptyList()
        }

        override suspend fun getAnimal(name: String): Animal? = null
    }

    /** An [AnimalRepository] that can't be reached. */
    private class UnreachableAnimalRepository : AnimalRepository {
        override suspend fun searchAnimals(query: String): List<String> =
            throw AnimalLookupException("Searching \"$query\" failed")

        override suspend fun getAnimal(name: String): Animal? =
            throw AnimalLookupException("Reading \"$name\" failed")
    }

    /** An [AnimalRepository] whose searches each wait until the test finishes them. */
    private class SlowAnimalRepository : AnimalRepository {
        private val results = mutableMapOf<String, CompletableDeferred<List<String>>>()

        override suspend fun searchAnimals(query: String): List<String> = resultFor(query).await()

        override suspend fun getAnimal(name: String): Animal? = null

        fun finish(query: String, animals: List<String>) {
            resultFor(query).complete(animals)
        }

        private fun resultFor(query: String) = results.getOrPut(query) { CompletableDeferred() }
    }
}
