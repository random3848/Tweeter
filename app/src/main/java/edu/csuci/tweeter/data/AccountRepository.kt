package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Who is logged in.
 *
 * Anonymous Firebase users count as logged out, so [loggedInUid] is null for them.
 */
interface AccountRepository {
    /** The logged-in user's Firebase Authentication ID, or null while nobody is logged in. */
    val loggedInUid: StateFlow<String?>

    /**
     * Signs in to an existing email/password account.
     *
     * @param email The account's email address.
     * @param password The account's password.
     */
    suspend fun logIn(email: String, password: String)

    /**
     * Creates an email/password account, linking the anonymous session when available.
     *
     * @param email The new account's email address.
     * @param password The new account's password.
     */
    suspend fun createAccount(email: String, password: String)

    /** Signs out of the registered account and restores anonymous guest access. */
    suspend fun logOut()

    /**
     * Requests a password reset email.
     *
     * @param email The account's email address.
     */
    suspend fun resetPassword(email: String)
}
