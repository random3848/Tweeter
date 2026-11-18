package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An [AccountRepository] for tests: user [initialUid] starts logged in, [logInAs] logs another
 * user in, and [logOut] logs them out. Logging in with an email and password and creating
 * accounts aren't needed by any test yet.
 */
class FakeAccountRepository(initialUid: String? = null) : AccountRepository {
    private val uid = MutableStateFlow(initialUid)

    /** How many times [logOut] was called. */
    var logOutCount = 0
        private set

    override val loggedInUid: StateFlow<String?> = uid.asStateFlow()

    /** Logs user [uid] in, as logging in with their email and password would. */
    fun logInAs(uid: String) {
        this.uid.value = uid
    }

    override suspend fun logIn(email: String, password: String) {
        error("Not needed by tests")
    }

    override suspend fun createAccount(email: String, password: String) {
        error("Not needed by tests")
    }

    override suspend fun logOut() {
        logOutCount++
        uid.value = null
    }

    override suspend fun resetPassword(email: String) {
        error("Not needed by tests")
    }
}
