@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.auth

import androidx.annotation.StringRes
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.FakeAccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val EMAIL = "maya@example.com"
private const val PASSWORD = "hawk-watcher"
private const val OTHER_PASSWORD = "owl-watcher"
private const val ERROR_CODE = "ERROR_TEST"

class AuthViewModelTest {
    private val accountRepository = FakeAccountRepository()
    private val viewModel = AuthViewModel(accountRepository)

    @Before
    fun useTestMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun logIn_accepted_authenticatesAndLocksForm() {
        viewModel.logIn(EMAIL, PASSWORD)

        assertTrue(viewModel.hasAuthenticated)
        assertFalse(viewModel.acceptsInput)
    }

    @Test
    fun logIn_accepted_logsInWithoutCreatingAccount() {
        viewModel.logIn(EMAIL, PASSWORD)

        assertEquals(1, accountRepository.logInCount)
        assertEquals(0, accountRepository.createAccountCount)
    }

    @Test
    fun logIn_rejected_showsInvalidCredentialsAndReenablesForm() {
        accountRepository.failure = invalidCredentials()

        viewModel.logIn(EMAIL, PASSWORD)

        assertEquals(R.string.auth_invalid_credentials, viewModel.shownErrorId)
        assertFalse(viewModel.hasAuthenticated)
        assertTrue(viewModel.acceptsInput)
    }

    @Test
    fun logIn_offline_showsNetworkError() {
        accountRepository.failure = FirebaseNetworkException("Offline")

        viewModel.logIn(EMAIL, PASSWORD)

        assertEquals(R.string.auth_network_error, viewModel.shownErrorId)
    }

    @Test
    fun logIn_unknownFirebaseFailure_showsGenericFailure() {
        accountRepository.failure = FirebaseException("Could not log in")

        viewModel.logIn(EMAIL, PASSWORD)

        assertEquals(R.string.auth_failed, viewModel.shownErrorId)
    }

    @Test
    fun logIn_authenticationStateError_showsGenericFailure() {
        accountRepository.failure = IllegalStateException("No authentication state")

        viewModel.logIn(EMAIL, PASSWORD)

        assertEquals(R.string.auth_failed, viewModel.shownErrorId)
        assertTrue(viewModel.acceptsInput)
    }

    @Test
    fun logIn_blankEmail_asksForCredentialsWithoutRequest() {
        viewModel.logIn(" ", PASSWORD)

        assertEquals(R.string.auth_missing_credentials, viewModel.shownErrorId)
        assertEquals(0, accountRepository.logInCount)
    }

    @Test
    fun logIn_emptyPassword_asksForCredentialsWithoutRequest() {
        viewModel.logIn(EMAIL, "")

        assertEquals(R.string.auth_missing_credentials, viewModel.shownErrorId)
        assertEquals(0, accountRepository.logInCount)
    }

    @Test
    fun logIn_whileRequestRuns_waitsWithFormLocked() {
        accountRepository.holdRequests()

        viewModel.logIn(EMAIL, PASSWORD)

        assertTrue(viewModel.isWaiting)
        assertFalse(viewModel.acceptsInput)
    }

    @Test
    fun logIn_secondSubmitWhileRequestRuns_isIgnored() {
        accountRepository.holdRequests()
        viewModel.logIn(EMAIL, PASSWORD)

        viewModel.logIn(EMAIL, PASSWORD)

        assertEquals(1, accountRepository.logInCount)
    }

    @Test
    fun logIn_requestFinishes_stopsWaiting() {
        accountRepository.holdRequests()
        viewModel.logIn(EMAIL, PASSWORD)

        accountRepository.releaseRequests()

        assertFalse(viewModel.isWaiting)
        assertTrue(viewModel.hasAuthenticated)
    }

    @Test
    fun logIn_afterAuthenticated_isIgnored() {
        viewModel.logIn(EMAIL, PASSWORD)

        viewModel.logIn(EMAIL, PASSWORD)

        assertEquals(1, accountRepository.logInCount)
    }

    @Test
    fun logIn_afterFailure_hidesErrorWhileWaiting() {
        accountRepository.failure = invalidCredentials()
        viewModel.logIn(EMAIL, PASSWORD)
        accountRepository.failure = null
        accountRepository.holdRequests()

        viewModel.logIn(EMAIL, PASSWORD)

        assertNull(viewModel.shownErrorId)
        assertTrue(viewModel.isWaiting)
    }

    @Test
    fun logIn_afterResetSent_hidesResetConfirmation() {
        viewModel.resetPassword(EMAIL)

        viewModel.logIn(EMAIL, "")

        assertFalse(viewModel.hasSentReset)
    }

    @Test
    fun createAccount_matchingPasswords_createsAccountAndAuthenticates() {
        viewModel.createAccount(EMAIL, PASSWORD, PASSWORD)

        assertEquals(1, accountRepository.createAccountCount)
        assertEquals(0, accountRepository.logInCount)
        assertTrue(viewModel.hasAuthenticated)
    }

    @Test
    fun createAccount_passwordMismatch_showsMismatchWithoutRequest() {
        viewModel.createAccount(EMAIL, PASSWORD, OTHER_PASSWORD)

        assertEquals(R.string.auth_password_mismatch, viewModel.shownErrorId)
        assertEquals(0, accountRepository.createAccountCount)
        assertTrue(viewModel.acceptsInput)
    }

    @Test
    fun createAccount_emailInUse_showsEmailInUseAndReenablesForm() {
        accountRepository.failure = FirebaseAuthUserCollisionException(ERROR_CODE, "Email in use")

        viewModel.createAccount(EMAIL, PASSWORD, PASSWORD)

        assertEquals(R.string.auth_email_in_use, viewModel.shownErrorId)
        assertFalse(viewModel.hasAuthenticated)
        assertTrue(viewModel.acceptsInput)
    }

    @Test
    fun createAccount_blankEmail_asksForCredentialsWithoutRequest() {
        viewModel.createAccount("", PASSWORD, PASSWORD)

        assertEquals(R.string.auth_missing_credentials, viewModel.shownErrorId)
        assertEquals(0, accountRepository.createAccountCount)
    }

    @Test
    fun createAccount_secondSubmitWhileRequestRuns_isIgnored() {
        accountRepository.holdRequests()
        viewModel.createAccount(EMAIL, PASSWORD, PASSWORD)

        viewModel.createAccount(EMAIL, PASSWORD, OTHER_PASSWORD)

        assertEquals(1, accountRepository.createAccountCount)
        assertNull(viewModel.shownErrorId)
    }

    @Test
    fun resetPassword_accepted_confirmsAndKeepsFormOpen() {
        viewModel.resetPassword(EMAIL)

        assertTrue(viewModel.hasSentReset)
        assertFalse(viewModel.hasAuthenticated)
        assertTrue(viewModel.acceptsInput)
    }

    @Test
    fun resetPassword_rejected_showsResetFailedAndReenablesForm() {
        accountRepository.failure = invalidCredentials()

        viewModel.resetPassword(EMAIL)

        assertEquals(R.string.auth_reset_failed, viewModel.shownErrorId)
        assertFalse(viewModel.hasSentReset)
        assertTrue(viewModel.acceptsInput)
    }

    @Test
    fun resetPassword_offline_showsNetworkError() {
        accountRepository.failure = FirebaseNetworkException("Offline")

        viewModel.resetPassword(EMAIL)

        assertEquals(R.string.auth_network_error, viewModel.shownErrorId)
    }

    @Test
    fun resetPassword_blankEmail_asksForEmailWithoutRequest() {
        viewModel.resetPassword(" ")

        assertEquals(R.string.auth_missing_email, viewModel.shownErrorId)
        assertEquals(0, accountRepository.resetPasswordCount)
    }

    @Test
    fun resetPassword_whileRequestRuns_waitsWithFormLocked() {
        accountRepository.holdRequests()

        viewModel.resetPassword(EMAIL)

        assertTrue(viewModel.isWaiting)
        assertFalse(viewModel.acceptsInput)
    }

    @Test
    fun resetPassword_whileLogInRuns_isIgnored() {
        accountRepository.holdRequests()
        viewModel.logIn(EMAIL, PASSWORD)

        viewModel.resetPassword(EMAIL)

        assertEquals(0, accountRepository.resetPasswordCount)
    }

    @Test
    fun logIn_whileResetRuns_isIgnored() {
        accountRepository.holdRequests()
        viewModel.resetPassword(EMAIL)

        viewModel.logIn(EMAIL, PASSWORD)

        assertEquals(0, accountRepository.logInCount)
    }

    @Test
    fun resetPassword_afterAuthenticated_isIgnored() {
        viewModel.logIn(EMAIL, PASSWORD)

        viewModel.resetPassword(EMAIL)

        assertEquals(0, accountRepository.resetPasswordCount)
    }

    @Test
    fun clearError_afterError_hidesIt() {
        viewModel.logIn("", "")

        viewModel.clearError()

        assertNull(viewModel.shownErrorId)
    }

    @Test
    fun clearError_afterResetSent_hidesResetConfirmation() {
        viewModel.resetPassword(EMAIL)

        viewModel.clearError()

        assertFalse(viewModel.hasSentReset)
    }

    @Test
    fun clearError_whileRequestRuns_keepsWaiting() {
        accountRepository.holdRequests()
        viewModel.logIn(EMAIL, PASSWORD)

        viewModel.clearError()

        assertTrue(viewModel.isWaiting)
    }

    @Test
    fun clearError_afterAuthenticated_staysAuthenticated() {
        viewModel.logIn(EMAIL, PASSWORD)

        viewModel.clearError()

        assertTrue(viewModel.hasAuthenticated)
    }

    private fun invalidCredentials() =
        FirebaseAuthInvalidCredentialsException(ERROR_CODE, "Wrong password")

    /** Whether the form's fields and buttons are enabled, as the screen decides it. */
    private val AuthViewModel.acceptsInput: Boolean
        get() = !isSubmitting && !isAuthenticated

    private val AuthViewModel.isWaiting: Boolean
        get() = isSubmitting

    private val AuthViewModel.hasAuthenticated: Boolean
        get() = isAuthenticated

    private val AuthViewModel.hasSentReset: Boolean
        get() = passwordResetSent

    @get:StringRes
    private val AuthViewModel.shownErrorId: Int?
        get() = errorMessageId
}
