package edu.csuci.tweeter.ui.following

import android.util.Log
import edu.csuci.tweeter.data.FollowException
import edu.csuci.tweeter.data.FollowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update

private const val TAG = "FollowedUsersReader"

/**
 * Reads the users someone follows for a screen that shows their posts, such as Map or Feed, each
 * holding its own reader.
 *
 * Reading the same user's followed users again, as when the screen is shown again, starts from
 * those read last, so the screen keeps showing them meanwhile instead of loading.
 *
 * @param followRepository Who follows whom.
 */
class FollowedUsersReader(private val followRepository: FollowRepository) {
    // How many times the user asked to read who they follow again after reading it failed.
    private val retryCount = MutableStateFlow(0)

    // The followed users read last, and who follows them.
    private val lastRead = MutableStateFlow<FollowedUsersRead?>(null)

    /**
     * The users user [uid] follows, updated as they change: loading until they are first read,
     * and unknown once they can't be, until [retry] is called.
     */
    fun followedBy(uid: String): Flow<FollowedUsers> = followRepository.observeFollowedUids(uid)
        .map { followedUids -> FollowedUsers.Known(followedUids) }
        .onEach { followed -> lastRead.value = FollowedUsersRead(uid, followed) }
        .onStart<FollowedUsers> { emit(lastReadBy(uid) ?: FollowedUsers.Loading) }
        .retryWhen { error, _ ->
            if (error !is FollowException) {
                return@retryWhen false
            }
            Log.e(TAG, "Could not read the users the user follows", error)
            // Read before saying so, so a retry asked for once the user sees it isn't missed.
            val retriesSoFar = retryCount.value
            emit(FollowedUsers.Unknown)
            retryCount.first { it > retriesSoFar }
            true
        }

    /** Reads who the user follows again, if reading it failed. */
    fun retry() {
        retryCount.update { it + 1 }
    }

    private fun lastReadBy(uid: String): FollowedUsers.Known? =
        lastRead.value?.takeIf { it.followerUid == uid }?.followed

    // The users [followerUid] follows, as read.
    private data class FollowedUsersRead(val followerUid: String, val followed: FollowedUsers.Known)
}
