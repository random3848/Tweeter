package edu.csuci.tweeter.ui.post

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AnimalRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The animal field of a new post: suggests animals as the user types and holds the one they
 * choose.
 *
 * A post names its animal with one of the suggestions or leaves it empty, so the field is
 * [isComplete] only then; text the user typed without choosing a suggestion isn't posted. A post
 * that names no animal asks others what it is, which the user can also say outright with
 * [markUnknown].
 *
 * The dialog that votes for another animal on a post's animal
 * ([edu.csuci.tweeter.ui.voting.AnimalVoteDialog]) searches with it too, and only reads
 * [query] and [suggestions].
 *
 * @param animalRepository Where the suggestions come from.
 */
class AnimalPickerViewModel(private val animalRepository: AnimalRepository) : ViewModel() {
    private var searchJob: Job? = null

    /** The text in the field. */
    var query by mutableStateOf("")
        private set

    /** The animal chosen from the suggestions, or null while none is. */
    var chosenAnimal by mutableStateOf<String?>(null)
        private set

    /** Animals matching [query], best first; empty once one is chosen or the field is blank. */
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set

    /**
     * Whether the user said they don't know what animal it is ([markUnknown]), until they type or
     * choose one.
     */
    var isUnknown by mutableStateOf(false)
        private set

    /** Whether the field is empty or holds a chosen animal, so the post can be shared. */
    val isComplete: Boolean
        get() = query.isBlank() || chosenAnimal != null

    /**
     * Sets the field to [text] the user typed, forgetting any chosen animal and that they didn't
     * know it, and searches it.
     */
    fun updateQuery(text: String) {
        query = text
        chosenAnimal = null
        isUnknown = false
        searchJob?.cancel()
        // Cancelling the previous search keeps its late results from replacing these.
        searchJob = viewModelScope.launch {
            suggestions = animalRepository.searchAnimals(text)
        }
    }

    /** Chooses [animal], one of the [suggestions], as the post's animal. */
    fun chooseAnimal(animal: String) {
        searchJob?.cancel()
        query = animal
        chosenAnimal = animal
        isUnknown = false
        suggestions = emptyList()
    }

    /** Empties the field, as for a new post. */
    fun clear() {
        searchJob?.cancel()
        query = ""
        chosenAnimal = null
        isUnknown = false
        suggestions = emptyList()
    }

    /**
     * Says the user doesn't know what animal it is, which empties the field, so that the post
     * names none and asks others.
     */
    fun markUnknown() {
        clear()
        isUnknown = true
    }

    /** Creates ViewModels that search the app's shared [AnimalRepository]. */
    companion object {
        /** Supplies the animal repository from the application container. */
        val factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                AnimalPickerViewModel(animalRepository = application.container.animalRepository)
            }
        }
    }
}
