package edu.csuci.tweeter.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import edu.csuci.tweeter.data.getLoggedInProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * Holds whose picture [ProfileButton] shows.
 *
 * @param profileRepository Where the logged-in user's profile is read from.
 * @param accountRepository Who is logged in.
 */
class ProfileButtonViewModel(
    profileRepository: ProfileRepository,
    accountRepository: AccountRepository
) : ViewModel() {
    /** The logged-in user's profile, or null while nobody is logged in. */
    val profile: StateFlow<UserProfile?> = profileRepository.getLoggedInProfile(accountRepository)
        .catch { error ->
            // Keep showing the last picture.
            Log.e("ProfileButtonViewModel", "Could not read the profile", error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    companion object {
        /** Creates a [ProfileButtonViewModel] that reads the app's shared repositories. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                ProfileButtonViewModel(
                    profileRepository = application.container.profileRepository,
                    accountRepository = application.container.accountRepository
                )
            }
        }
    }
}
