package edu.csuci.tweeter.ui.post

import edu.csuci.tweeter.data.AnimalRepository
import edu.csuci.tweeter.data.FakeAnimalRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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

        viewModel.updateQuery("hawk")

        assertEquals(listOf("Hawk moth", "Red-tailed hawk"), viewModel.suggestions)
    }

    @Test
    fun updateQuery_textNotChosenFromSuggestions_isNotComplete() {
        val viewModel = createViewModel()

        viewModel.updateQuery("hawk")

        assertFalse(viewModel.isComplete)
    }

    @Test
    fun updateQuery_blank_isCompleteWithoutSuggestions() {
        val viewModel = createViewModel()
        viewModel.updateQuery("hawk")

        viewModel.updateQuery(" ")

        assertEquals(emptyList<String>(), viewModel.suggestions)
        assertTrue(viewModel.isComplete)
    }

    @Test
    fun chooseAnimal_fillsFieldAndHidesSuggestions() {
        val viewModel = createViewModel()
        viewModel.updateQuery("fox")

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

        viewModel.updateQuery("Island fo")

        assertNull(viewModel.chosenAnimal)
        assertFalse(viewModel.isComplete)
    }

    @Test
    fun clear_emptiesField() {
        val viewModel = createViewModel()
        viewModel.updateQuery("fox")
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
        viewModel.updateQuery("ha")
        viewModel.updateQuery("hawk")

        repository.finish("hawk", listOf("Red-tailed hawk"))
        repository.finish("ha", listOf("Harbor seal", "Red-tailed hawk"))

        assertEquals(listOf("Red-tailed hawk"), viewModel.suggestions)
    }

    @Test
    fun chooseAnimal_whileSearching_doesNotShowSuggestionsAfterward() {
        val repository = SlowAnimalRepository()
        val viewModel = AnimalPickerViewModel(repository)
        viewModel.updateQuery("fox")

        viewModel.chooseAnimal("Island fox")
        repository.finish("fox", listOf("Island fox"))

        assertEquals(emptyList<String>(), viewModel.suggestions)
    }

    private fun createViewModel() = AnimalPickerViewModel(FakeAnimalRepository(animals))

    /** An [AnimalRepository] whose searches each wait until the test finishes them. */
    private class SlowAnimalRepository : AnimalRepository {
        private val results = mutableMapOf<String, CompletableDeferred<List<String>>>()

        override suspend fun searchAnimals(query: String): List<String> = resultFor(query).await()

        fun finish(query: String, animals: List<String>) {
            resultFor(query).complete(animals)
        }

        private fun resultFor(query: String) = results.getOrPut(query) { CompletableDeferred() }
    }
}
