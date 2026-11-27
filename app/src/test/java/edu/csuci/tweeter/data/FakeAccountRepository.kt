package edu.csuci.tweeter.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An [AccountRepository] for tests: user [initialUid] starts logged in, [logInAs] logs another
 * user in, and [logOut] logs them out.
 *
 * Logging in with an email and password, creating an account and requesting a password reset
 * are counted and succeed without changing [loggedInUid], or throw [failure] when it's set. While
 * [holdRequests] holds them, they wait for [releaseRequests].
 */
class FakeAccountRepository(initialUid: String? = null) : AccountRepository {
    private val uid = MutableStateFlow(initialUid)
    private var release: CompletableDeferred<Unit>? = null

    /** What logging in, creating an account and requesting a reset throw, or null to succeed. */
    var failure: Exception? = null

    /** How many times [logIn] was called. */
    var logInCount = 0
        private set

    /** How many times [createAccount] was called. */
    var createAccountCount = 0
        private set

    /** How many times [resetPassword] was called. */
    var resetPasswordCount = 0
        private set

    /** How many times [logOut] was called. */
    var logOutCount = 0
        private set

    override val loggedInUid: StateFlow<String?> = uid.asStateFlow()

    /** Logs user [uid] in, as logging in with their email and password would. */
    fun logInAs(uid: String) {
        this.uid.value = uid
    }

    /** Makes the requests that start from now on wait until [releaseRequests]. */
    fun holdRequests() {
        release = CompletableDeferred()
    }

    /** Lets the waiting requests finish, and the next ones finish without waiting. */
    fun releaseRequests() {
        release?.complete(Unit)
        release = null
    }

    override suspend fun logIn(email: String, password: String) {
        logInCount++
        finishRequest()
    }

    override suspend fun createAccount(email: String, password: String) {
        createAccountCount++
        finishRequest()
    }

    override suspend fun logOut() {
        logOutCount++
        uid.value = null
    }

    override suspend fun resetPassword(email: String) {
        resetPasswordCount++
        finishRequest()
    }

    private suspend fun finishRequest() {
        release?.await()
        failure?.let { throw it }
    }
}
