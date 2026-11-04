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
 * The registered user's profile and posts for ProfileScreen.
 *
 * Observes account changes so successful authentication updates Profile.
 *
 * @param accountRepository Supplies the registered user's identity.
 * @param profileRepository Where the profile is read from.
 * @param postRepository Where the user's posts are read from.
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
