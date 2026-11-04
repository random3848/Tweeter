package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Who is logged in.
 *
 * Feed and Map work without logging in; Profile needs it. The app uses
 * [PlaceholderAccountRepository] until create-account/login is built.
 */
interface AccountRepository {
    /** The logged-in user's Firebase Authentication ID, or null while nobody is logged in. */
    val loggedInUid: StateFlow<String?>
}
