package edu.csuci.tweeter.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.ThemeRepository
import edu.csuci.tweeter.ui.theme.ThemeChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the Settings screen's logout action stands. */
enum class SettingsStatus {
    /** Ready to log out. */
    READY,

    /** Waiting for logout and guest-session restoration. */
    LOGGING_OUT,

    /** Logout failed while the registered account is still signed in. */
    FAILED,

    /** The registered account is signed out, so Settings should close. */
    LOGGED_OUT
}

/**
 * Account actions and preferences available from Settings.
 *
 * @param accountRepository Supplies the session and signs the user out.
 * @param themeRepository Where the user's theme choice is saved and read.
 */
class SettingsViewModel(
    private val accountRepository: AccountRepository,
    private val themeRepository: ThemeRepository
) : ViewModel() {
    private val _status = MutableStateFlow(SettingsStatus.READY)

    /** The logout action's current status. */
    val status = _status.asStateFlow()

    /** The user's theme choice (Light or Dark). */
    val themeChoice: StateFlow<ThemeChoice> = themeRepository.themeChoice

    /** Sets the user's theme choice. */
    fun setThemeChoice(choice: ThemeChoice) {
        themeRepository.setThemeChoice(choice)
    }

    /** Signs out once, allowing another attempt if the previous attempt failed. */
    fun logOut() {
        val current = _status.value
        if (current != SettingsStatus.READY && current != SettingsStatus.FAILED) {
            return
        }
        if (!_status.compareAndSet(current, SettingsStatus.LOGGING_OUT)) {
            return
        }

        viewModelScope.launch {
            try {
                accountRepository.logOut()
                _status.value = SettingsStatus.LOGGED_OUT
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("SettingsViewModel", "Could not complete logout", error)
                _status.value = if (accountRepository.loggedInUid.value == null) {
                    SettingsStatus.LOGGED_OUT
                } else {
                    SettingsStatus.FAILED
                }
            }
        }
    }

    companion object {
        /** Creates Settings using the app's shared account and theme repositories. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                SettingsViewModel(
                    accountRepository = application.container.accountRepository,
                    themeRepository = application.container.themeRepository
                )
            }
        }
    }
}
