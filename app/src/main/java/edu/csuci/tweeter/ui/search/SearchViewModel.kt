package edu.csuci.tweeter.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * What Search found for its query.
 *
 * @property people The users whose name contains the query, those starting with it first.
 * @property animals The animals posts name that contain the query, those starting with it first.
 * @property isLoadingPeople Whether the profiles are still being read, so [people] can't be told
 *     yet.
 */
data class SearchResults(
    val people: List<UserProfile> = emptyList(),
    val animals: List<PostedAnimal> = emptyList(),
    val isLoadingPeople: Boolean = true
)

/**
 * Holds what [SearchScreen] shows: the text the user searches for, and the people and animals
 * whose names contain it.
 *
 * @param profileRepository Where the people come from: everyone who has set up a profile.
 * @param postRepository Where the animals come from: those the posts name.
 * @param searchDispatcher Where the animals are counted and the results found: off the main
 *     thread, since counting them is a pass over every post.
 * @param sharing When [results] follows the profiles and posts: while Search collects it, in the
 *     app.
 */
class SearchViewModel(
    profileRepository: ProfileRepository,
    postRepository: PostRepository,
    searchDispatcher: CoroutineDispatcher,
    sharing: SharingStarted
) : ViewModel() {
    /** The text in the search field. */
    var query by mutableStateOf("")
        private set

    /** The people and animals found for [query], updated as it and they change. */
    val results: StateFlow<SearchResults> = combine(
        snapshotFlow { query },
        profileRepository.getProfiles().onStart<List<UserProfile>?> { emit(null) },
        postRepository.getPostTable().map(::postedAnimalsIn)
    ) { query, profiles, animals ->
        SearchResults(
            people = profiles?.filterByName(query, UserProfile::displayName).orEmpty(),
            animals = animals.filterByName(query, PostedAnimal::name),
            isLoadingPeople = profiles == null
        )
    }
        .flowOn(searchDispatcher)
        .stateIn(viewModelScope, sharing, SearchResults())

    /** Searches for [text], which the user typed. */
    fun updateQuery(text: String) {
        // Applied as a snapshot of its own, so that [results] hears of it at once, not only once
        // Compose next applies the changes made outside its snapshots.
        Snapshot.withMutableSnapshot { query = text }
    }

    companion object {
        /** Creates a [SearchViewModel] that reads the app's shared profiles and posts. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                SearchViewModel(
                    profileRepository = application.container.profileRepository,
                    postRepository = application.container.postRepository,
                    searchDispatcher = Dispatchers.Default,
                    sharing = application.container.whileScreenCollects
                )
            }
        }
    }
}
