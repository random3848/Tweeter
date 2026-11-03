package edu.csuci.tweeter.ui.auth

import android.util.Log
import androidx.annotation.StringRes
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
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import edu.csuci.tweeter.R
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import kotlinx.coroutines.launch

/**
 * Authentication form submissions and their results.
 *
 * Credentials are passed to the repository without being saved in persistent state.
 *
 * @param accountRepository The app's shared authentication repository.
 */
class AuthViewModel(private val accountRepository: AccountRepository) : ViewModel() {
    /** Whether an authentication request is running. */
    var isSubmitting by mutableStateOf(false)
        private set

    /** Whether this form successfully authenticated the user. */
    var isAuthenticated by mutableStateOf(false)
        private set

    /** Whether a password reset request completed successfully. */
    var passwordResetSent by mutableStateOf(false)
        private set

    /** The string resource describing the latest error, or null without an error. */
    @get:StringRes
    var errorMessageId: Int? by mutableStateOf(null)
        private set

    /**
     * Signs in using the supplied credentials.
     *
     * @param email The account's email address.
     * @param password The account's password.
     */
    fun logIn(email: String, password: String) {
        submit(email, password, creatingAccount = false)
    }

    /**
     * Registers an account after checking that the passwords match.
     *
     * @param email The new account's email address.
     * @param password The new account's password.
     * @param confirmation The repeated password.
     */
    fun createAccount(email: String, password: String, confirmation: String) {
        if (isSubmitting || isAuthenticated) {
            return
        }

        if (password != confirmation) {
            errorMessageId = R.string.auth_password_mismatch
            return
        }

        submit(email, password, creatingAccount = true)
    }

    /** Clears the previous error when the user edits the form. */
    fun clearError() {
        errorMessageId = null
        passwordResetSent = false
    }

    private fun submit(email: String, password: String, creatingAccount: Boolean) {
        if (isSubmitting || isAuthenticated) {
            return
        }

        clearError()

        if (email.isBlank() || password.isEmpty()) {
            errorMessageId = R.string.auth_missing_credentials
            return
        }

        isSubmitting = true

        viewModelScope.launch {
            try {
                if (creatingAccount) {
                    accountRepository.createAccount(email, password)
                } else {
                    accountRepository.logIn(email, password)
                }
                isAuthenticated = true
            } catch (error: FirebaseException) {
                Log.e("AuthViewModel", "Firebase authentication failed", error)
                errorMessageId = when (error) {
                    is FirebaseAuthWeakPasswordException -> R.string.auth_weak_password
                    is FirebaseAuthUserCollisionException -> R.string.auth_email_in_use
                    is FirebaseAuthInvalidCredentialsException -> R.string.auth_invalid_credentials
                    is FirebaseNetworkException -> R.string.auth_network_error
                    is FirebaseTooManyRequestsException -> R.string.auth_too_many_requests
                    else -> R.string.auth_failed
                }
            } catch (error: IllegalStateException) {
                Log.e("AuthViewModel", "Authentication state error", error)
                errorMessageId = R.string.auth_failed
            } finally {
                isSubmitting = false
            }
        }
    }

    /**
     * Requests a reset link for the email entered on the login form.
     *
     * @param email The account's email address.
     */
    fun resetPassword(email: String) {
        if (isSubmitting || isAuthenticated) {
            return
        }

        clearError()

        if (email.isBlank()) {
            errorMessageId = R.string.auth_missing_email
            return
        }

        isSubmitting = true

        viewModelScope.launch {
            try {
                accountRepository.resetPassword(email)
                passwordResetSent = true
            } catch (error: FirebaseException) {
                errorMessageId = when (error) {
                    is FirebaseNetworkException -> R.string.auth_network_error
                    is FirebaseTooManyRequestsException -> R.string.auth_too_many_requests
                    else -> R.string.auth_reset_failed
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    companion object {
        /** Creates the ViewModel using the app's shared account repository. */
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                AuthViewModel(
                    accountRepository = application.container.accountRepository
                )
            }
        }
    }
}
