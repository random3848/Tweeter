package edu.csuci.tweeter.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Email/password authentication backed by Firebase.
 *
 * Anonymous/Guest sessions are treated as logged out. Registration links credentials
 * to an existing anonymous user so their data keeps the same UID.
 *
 * Created once by AppContainer for the application's lifetime.
 *
 * @param auth The app's shared Firebase Authentication instance.
 * @param guestSignIn Signs a guest in again once the user logs out, sharing a sign-in that's
 *     already running instead of creating a second anonymous account.
 */
class FirebaseAccountRepository(
    private val auth: FirebaseAuth,
    private val guestSignIn: GuestSignIn
) : AccountRepository {
    private val _loggedInUid = MutableStateFlow(registeredUid())

    override val loggedInUid: StateFlow<String?> = _loggedInUid.asStateFlow()

    init {
        auth.addAuthStateListener {
            updateLoggedInUser()
        }
    }

    /**
     * Signs in to an existing email/password account.
     *
     * @param email The account's email address.
     * @param password The account's password.
     */
    override suspend fun logIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        updateLoggedInUser()
    }

    /**
     * Creates an account while preserving an existing anonymous user's UID.
     *
     * @param email The new account's email address.
     * @param password The new account's password.
     */
    override suspend fun createAccount(email: String, password: String) {
        val user = auth.currentUser

        check(user == null || user.isAnonymous) {
            "Sign out before creating another account"
        }

        if (user != null) {
            val credential = EmailAuthProvider.getCredential(email.trim(), password)
            user.linkWithCredential(credential).await()
        } else {
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
        }

        updateLoggedInUser()
    }

    override suspend fun logOut() {
        auth.signOut()
        updateLoggedInUser()

        // Feed and Map need a Firebase session even while browsing as a guest.
        guestSignIn.signInIfSignedOut()

        updateLoggedInUser()
    }

    override suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    private fun updateLoggedInUser() {
        _loggedInUid.value = registeredUid()
    }

    private fun registeredUid(): String? {
        val user = auth.currentUser
        return if (user != null && !user.isAnonymous) user.uid else null
    }
}
