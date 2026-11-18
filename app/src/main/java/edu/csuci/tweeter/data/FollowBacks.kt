package edu.csuci.tweeter.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Whether user [userUid] follows each of their followers back, as [observeFriendUids] reads it:
 * one listener per follower, kept open while they follow [userUid].
 *
 * Not thread-safe: [readFollowers] is called from one coroutine at a time.
 *
 * @param repository Where the relationships are read.
 * @param userUid The user whose followers' relationships are read.
 * @param scope Where each follower's listener runs; its failure fails the scope.
 */
internal class FollowBacks(
    private val repository: FollowRepository,
    private val userUid: String,
    private val scope: CoroutineScope
) {
    // Whether the user follows each follower back, or null until that's read; the whole map is
    // null until the followers have been read.
    private val answers = MutableStateFlow<Map<String, Boolean?>?>(null)

    // The listener of each follower's relationship.
    private val reads = HashMap<String, Job>()

    /** The followers [userUid] follows back, each time every follower's relationship is known. */
    fun friendUids(): Flow<Set<String>> = answers.filterNotNull().mapNotNull { answers ->
        if (null in answers.values) null else answers.filterValues { it == true }.keys.toSet()
    }

    /**
     * Reads the relationships of [followerUids], the user's followers now: opens a listener for
     * each new follower, and closes those of users who stopped following.
     */
    fun readFollowers(followerUids: Set<String>) {
        stopReadingAllBut(followerUids)
        answers.update { known -> followerUids.associateWith { known?.get(it) } }
        for (followerUid in followerUids - reads.keys) {
            reads[followerUid] = scope.launch { readFollowBack(followerUid) }
        }
    }

    private fun stopReadingAllBut(followerUids: Set<String>) {
        val iterator = reads.entries.iterator()
        while (iterator.hasNext()) {
            val (followerUid, read) = iterator.next()
            if (followerUid !in followerUids) {
                read.cancel()
                iterator.remove()
            }
        }
    }

    private suspend fun readFollowBack(followerUid: String) {
        repository.observeFollowing(userUid, followerUid).collect { followsBack ->
            answers.update { known -> known?.withAnswer(followerUid, followsBack) }
        }
    }

    // These answers with [followsBack] for [followerUid], unless they stopped following since,
    // so that a listener's last answer can't bring back a follower who left.
    private fun Map<String, Boolean?>.withAnswer(
        followerUid: String,
        followsBack: Boolean
    ): Map<String, Boolean?> {
        val stillFollows = followerUid in this
        return if (stillFollows) this + (followerUid to followsBack) else this
    }
}
