package edu.csuci.tweeter.ui.friends

import android.util.Log
import edu.csuci.tweeter.data.FollowException
import edu.csuci.tweeter.data.FollowRepository
import edu.csuci.tweeter.data.observeFriendUids
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update

private const val TAG = "FriendsReader"

/**
 * Reads a user's friends for a screen that shows their posts, such as Map or Feed, each holding
 * its own reader.
 *
 * Reading the same user's friends again, as when the screen is shown again, starts from the
 * friends read last, so the screen keeps showing them meanwhile instead of loading.
 *
 * @param followRepository Who follows whom.
 */
class FriendsReader(private val followRepository: FollowRepository) {
    // How many times the user asked to read their friends again after reading them failed.
    private val retryCount = MutableStateFlow(0)

    // The friends read last, and whose they are.
    private val lastRead = MutableStateFlow<FriendsRead?>(null)

    /**
     * The friends of user [uid], updated as they change: loading until they are first read, and
     * unknown once they can't be, until [retry] is called.
     */
    fun friendsOf(uid: String): Flow<UserFriends> = followRepository.observeFriendUids(uid)
        .map { friendUids -> UserFriends.Known(friendUids) }
        .onEach { friends -> lastRead.value = FriendsRead(uid, friends) }
        .onStart<UserFriends> { emit(lastReadOf(uid) ?: UserFriends.Loading) }
        .retryWhen { error, _ ->
            if (error !is FollowException) {
                return@retryWhen false
            }
            Log.e(TAG, "Could not read the user's friends", error)
            // Read before saying so, so a retry asked for once the user sees it isn't missed.
            val retriesSoFar = retryCount.value
            emit(UserFriends.Unknown)
            retryCount.first { it > retriesSoFar }
            true
        }

    /** Reads the user's friends again, if reading them failed. */
    fun retry() {
        retryCount.update { it + 1 }
    }

    private fun lastReadOf(uid: String): UserFriends.Known? =
        lastRead.value?.takeIf { it.uid == uid }?.friends

    // The [friends] of user [uid], as read.
    private data class FriendsRead(val uid: String, val friends: UserFriends.Known)
}
