package edu.csuci.tweeter.data

import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * A stand-in for logging in until create-account/login is built; that work replaces this class.
 *
 * [logIn] logs in as the anonymous Firebase user the app already uses to read Feed and Map. It
 * only remembers that in memory, so the user is logged out again when the app process ends.
 *
 * An anonymous user's ID is lost when the app is reinstalled or its data is cleared, and with it
 * their profile (`users/{uid}`) and the link to their posts ([Post.authorUid]). Real login should
 * link the account to the anonymous user (`linkWithCredential`) to keep both.
 */
class PlaceholderAccountRepository(private val auth: FirebaseAuth) : AccountRepository {
    private val _loggedInUid = MutableStateFlow<String?>(null)

    override val loggedInUid: StateFlow<String?> = _loggedInUid.asStateFlow()

    /**
     * Logs in as the anonymous Firebase user, signing in anonymously first if needed.
     *
     * @throws FirebaseException if signing in fails.
     */
    suspend fun logIn() {
        // Anonymous until create-account/login is built; that work changes or removes this.
        val user = auth.currentUser ?:
            auth.signInAnonymously().await().user ?:
            throw FirebaseException("Firebase sign-in returned no user")
        _loggedInUid.value = user.uid
    }
}
