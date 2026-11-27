package edu.csuci.tweeter.ui.animal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.Animal
import edu.csuci.tweeter.data.AnimalLookupException
import edu.csuci.tweeter.data.AnimalRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the top of an animal's page shows about the animal. */
sealed interface AnimalDetailsUiState {
    /** Still looking the animal up. */
    data object Loading : AnimalDetailsUiState

    /**
     * What is known about the animal.
     *
     * @property animal The animal posts name.
     */
    data class Found(val animal: Animal) : AnimalDetailsUiState

    /** No animal has the name posts gave it, so only the posts are shown. */
    data object NotFound : AnimalDetailsUiState

    /** The animal couldn't be looked up; the user can try again. */
    data object Failed : AnimalDetailsUiState
}

/**
 * Looks up what is known about one animal, by the name posts give it, for the top of its page.
 *
 * @param animal The animal's name, as posts name it.
 * @param animalRepository Where the animal is looked up.
 */
class AnimalDetailsViewModel(
    private val animal: String,
    private val animalRepository: AnimalRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<AnimalDetailsUiState>(AnimalDetailsUiState.Loading)

    // The lookup running, which a new one replaces.
    private var lookupJob: Job? = null

    /** What is known about the animal so far. */
    val uiState: StateFlow<AnimalDetailsUiState> = _uiState.asStateFlow()

    init {
        lookUp()
    }

    /** Looks the animal up again, after a lookup failed. */
    fun retry() {
        lookUp()
    }

    private fun lookUp() {
        lookupJob?.cancel()
        _uiState.value = AnimalDetailsUiState.Loading
        lookupJob = viewModelScope.launch { _uiState.value = readDetails() }
    }

    private suspend fun readDetails(): AnimalDetailsUiState = try {
        val found = animalRepository.getAnimal(animal)
        if (found == null) AnimalDetailsUiState.NotFound else AnimalDetailsUiState.Found(found)
    } catch (error: AnimalLookupException) {
        Log.w(TAG, "Looking up \"$animal\" failed", error)
        AnimalDetailsUiState.Failed
    }

    /** Creates [AnimalDetailsViewModel]s. */
    companion object {
        private const val TAG = "AnimalDetailsViewModel"

        /**
         * Creates an [AnimalDetailsViewModel] that looks [animal] up in the app's shared
         * [AnimalRepository].
         */
        fun createFactory(animal: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                AnimalDetailsViewModel(
                    animal = animal,
                    animalRepository = application.container.animalRepository
                )
            }
        }
    }
}
