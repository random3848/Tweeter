package edu.csuci.tweeter.ui.profile

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.FirebaseException
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.PlaceholderAccountRepository
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
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
     * @property profile What they show about themselves.
     * @property posts The posts they shared, newest first.
     */
    data class LoggedIn(val profile: UserProfile, val posts: List<Post>) : ProfileUiState
}

/**
 * Holds the logged-in user's profile and posts for [ProfileScreen], and logs the user in from
 * its prompt.
 *
 * @param accountRepository Who is logged in. It is the placeholder until create-account/login is
 *     built, since [logIn] uses it.
 * @param profileRepository Where the profile is read from.
 * @param postRepository Where the user's posts are read from.
 */
class ProfileViewModel(
    private val accountRepository: PlaceholderAccountRepository,
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
                    postRepository.getPostsBy(uid)
                ) { profile, posts ->
                    ProfileUiState.LoggedIn(profile, posts)
                }.catch { error ->
                    // Keep showing the last profile.
                    Log.e("ProfileViewModel", "Could not read the profile", error)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState.Loading
        )

    /** Whether [logIn] is still logging the user in. */
    var isLoggingIn by mutableStateOf(false)
        private set

    /** Whether the latest attempt to log in failed. */
    var logInFailed by mutableStateOf(false)
        private set

    /** Logs the user in, after which [uiState] shows their profile. Ignored while one runs. */
    fun logIn() {
        if (isLoggingIn) {
            return
        }

        isLoggingIn = true
        logInFailed = false
        viewModelScope.launch {
            try {
                accountRepository.logIn()
            } catch (error: FirebaseException) {
                Log.e("ProfileViewModel", "Could not log in", error)
                logInFailed = true
            } finally {
                isLoggingIn = false
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
