package edu.csuci.tweeter.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.FirebaseException
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import java.util.Objects
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What [ProfileScreen] shows. */
sealed interface ProfileUiState {
    /** Still reading who is logged in and their profile. */
    data object Loading : ProfileUiState

    /** Nobody is logged in, so the screen asks the user to log in. */
    data object LoggedOut : ProfileUiState

    /**
     * The logged-in user's profile.
     *
     * Equal to another [LoggedIn] with the same profile, table contents and rows, so tests and
     * the state flow compare what is shown rather than which arrays hold it.
     *
     * @property profile What they show about themselves.
     * @property table Every post, newest first.
     * @property rows The rows of [table] holding the posts they shared, newest first.
     */
    class LoggedIn(val profile: UserProfile, val table: PostTable, val rows: IntArray) :
        ProfileUiState {
        override fun equals(other: Any?): Boolean = other is LoggedIn &&
            profile == other.profile &&
            table == other.table &&
            rows.contentEquals(other.rows)

        override fun hashCode(): Int = Objects.hash(profile, table, rows.contentHashCode())

        override fun toString(): String =
            "LoggedIn(profile=$profile, table=$table, rows=${rows.contentToString()})"
    }
}

/**
 * The registered user's profile and posts for ProfileScreen.
 *
 * Observes account changes so successful authentication updates Profile.
 *
 * @param accountRepository Supplies the registered user's identity.
 * @param profileRepository Where the profile is read from.
 * @param postRepository Where every post is read from; Profile shows the user's rows of it.
 */
class ProfileViewModel(
    private val accountRepository: AccountRepository,
    profileRepository: ProfileRepository,
    postRepository: PostRepository
) : ViewModel() {
    /** The current state of the screen, updated as the user logs in and the profile changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProfileUiState> = accountRepository.loggedInUid
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(ProfileUiState.LoggedOut)
            } else {
                combine(
                    profileRepository.getProfile(uid),
                    postRepository.getPostTable()
                ) { profile, table ->
                    ProfileUiState.LoggedIn(profile, table, table.rowsBy(uid))
                }.catch { error ->
                    // Keep showing the last profile.
                    Log.e("ProfileViewModel", "Could not read the profile", error)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = ProfileUiState.Loading
        )

    /** Signs out and restores the Firebase session used for guest browsing. */
    fun logOut() {
        viewModelScope.launch {
            try {
                accountRepository.logOut()
            } catch (error: FirebaseException) {
                Log.e(
                    "ProfileViewModel",
                    "Signed out, but could not restore guest access",
                    error
                )
            }
        }
    }

    companion object {
        /** Creates a [ProfileViewModel] that reads the app's shared repositories. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                ProfileViewModel(
                    accountRepository = application.container.accountRepository,
                    profileRepository = application.container.profileRepository,
                    postRepository = application.container.postRepository
                )
            }
        }
    }
}
