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
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import edu.csuci.tweeter.R
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.AccountRepository
import kotlinx.coroutines.launch

private const val TAG = "AuthViewModel"

/** Where the log-in or registration form stands. */
sealed interface AuthStatus {
    /** Whether the user can edit the form and send a request. */
    val acceptsInput: Boolean
        get() = when (this) {
            is Editing, ResetSent -> true
            Submitting, Authenticated -> false
        }

    /**
     * Filling in the form.
     *
     * @property errorMessageId The string resource saying why the last request failed or
     *     couldn't be sent, or null without an error.
     */
    data class Editing(@StringRes val errorMessageId: Int? = null) : AuthStatus

    /** A log-in, registration or password reset request is running. */
    data object Submitting : AuthStatus

    /** A password reset link was requested; the user can keep editing the form. */
    data object ResetSent : AuthStatus

    /** This form authenticated the user. */
    data object Authenticated : AuthStatus
}

/**
 * Authentication form submissions and their results.
 *
 * Credentials are passed to the repository without being saved in persistent state.
 *
 * @param accountRepository The app's shared authentication repository.
 */
class AuthViewModel(private val accountRepository: AccountRepository) : ViewModel() {
    /** Where the form stands. */
    var status: AuthStatus by mutableStateOf(AuthStatus.Editing())
        private set

    /**
     * Signs in using the supplied credentials.
     *
     * @param email The account's email address.
     * @param password The account's password.
     */
    fun logIn(email: String, password: String) {
        submit(credentialsErrorId(email, password)) {
            authenticate { accountRepository.logIn(email, password) }
        }
    }

    /**
     * Registers an account after checking that the passwords match.
     *
     * @param email The new account's email address.
     * @param password The new account's password.
     * @param confirmation The repeated password.
     */
    fun createAccount(email: String, password: String, confirmation: String) {
        val inputErrorId = if (password == confirmation) {
            credentialsErrorId(email, password)
        } else {
            R.string.auth_password_mismatch
        }
        submit(inputErrorId) {
            authenticate { accountRepository.createAccount(email, password) }
        }
    }

    /**
     * Requests a reset link for the email entered on the login form.
     *
     * @param email The account's email address.
     */
    fun resetPassword(email: String) {
        val inputErrorId = if (email.isBlank()) R.string.auth_missing_email else null
        submit(inputErrorId) { requestReset(email) }
    }

    /** Hides the previous error or reset confirmation when the user edits the form. */
    fun clearError() {
        if (status.acceptsInput) {
            status = AuthStatus.Editing()
        }
    }

    /**
     * Shows [inputErrorId] instead of sending [request] when it isn't null; otherwise waits for
     * [request] and moves to the status it returns. Does nothing while the form doesn't accept
     * input.
     */
    private fun submit(@StringRes inputErrorId: Int?, request: suspend () -> AuthStatus) {
        if (!status.acceptsInput) {
            return
        }

        if (inputErrorId != null) {
            status = AuthStatus.Editing(inputErrorId)
            return
        }

        status = AuthStatus.Submitting
        viewModelScope.launch {
            status = request()
        }
    }

    @StringRes
    private fun credentialsErrorId(email: String, password: String): Int? {
        val isMissingCredentials = email.isBlank() || password.isEmpty()
        return if (isMissingCredentials) R.string.auth_missing_credentials else null
    }

    private suspend fun authenticate(request: suspend () -> Unit): AuthStatus = try {
        request()
        AuthStatus.Authenticated
    } catch (error: FirebaseException) {
        Log.e(TAG, "Firebase authentication failed", error)
        AuthStatus.Editing(authenticationErrorId(error))
    } catch (error: IllegalStateException) {
        Log.e(TAG, "Authentication state error", error)
        AuthStatus.Editing(R.string.auth_failed)
    }

    @StringRes
    private fun authenticationErrorId(error: FirebaseException): Int = when (error) {
        is FirebaseAuthWeakPasswordException -> R.string.auth_weak_password
        is FirebaseAuthUserCollisionException -> R.string.auth_email_in_use
        is FirebaseAuthInvalidCredentialsException -> R.string.auth_invalid_credentials
        is FirebaseAuthInvalidUserException -> R.string.auth_invalid_credentials
        is FirebaseNetworkException -> R.string.auth_network_error
        is FirebaseTooManyRequestsException -> R.string.auth_too_many_requests
        else -> R.string.auth_failed
    }

    private suspend fun requestReset(email: String): AuthStatus = try {
        accountRepository.resetPassword(email)
        AuthStatus.ResetSent
    } catch (error: FirebaseException) {
        Log.e(TAG, "Password reset failed", error)
        AuthStatus.Editing(resetErrorId(error))
    }

    @StringRes
    private fun resetErrorId(error: FirebaseException): Int = when (error) {
        is FirebaseNetworkException -> R.string.auth_network_error
        is FirebaseTooManyRequestsException -> R.string.auth_too_many_requests
        else -> R.string.auth_reset_failed
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
