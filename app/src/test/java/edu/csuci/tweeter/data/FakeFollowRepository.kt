package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A [FollowRepository] for tests that keeps relationships in memory, which [addFollow] and
 * [removeFollow] change between any two users. Following and unfollowing as the logged-in user
 * aren't needed by any test that uses it.
 */
class FakeFollowRepository : FollowRepository {
    private val follows = MutableStateFlow(emptySet<Follow>())

    /** Whether reads fail, as when the server refuses them. */
    var failsReads = false

    /** Makes [followerUid] a follower of [followedUid]. */
    fun addFollow(followerUid: String, followedUid: String) {
        follows.update { it + Follow(followerUid, followedUid) }
    }

    /** Ends [followerUid]'s following of [followedUid]. */
    fun removeFollow(followerUid: String, followedUid: String) {
        follows.update { it - Follow(followerUid, followedUid) }
    }

    override fun observeFollowing(followerUid: String, followedUid: String): Flow<Boolean> =
        read { follows -> Follow(followerUid, followedUid) in follows }

    override fun observeFollowedUids(followerUid: String): Flow<Set<String>> = read { follows ->
        follows.filter { it.followerUid == followerUid }.mapTo(HashSet(), Follow::followedUid)
    }

    override fun observeFollowerUids(followedUid: String): Flow<Set<String>> = read { follows ->
        follows.filter { it.followedUid == followedUid }.mapTo(HashSet(), Follow::followerUid)
    }

    override suspend fun follow(userUid: String) {
        error("Not needed by tests")
    }

    override suspend fun unfollow(userUid: String) {
        error("Not needed by tests")
    }

    // [answer] for the relationships, read again each time they change, unless reads fail.
    private fun <T> read(answer: (Set<Follow>) -> T): Flow<T> = flow {
        if (failsReads) {
            throw FollowException(IllegalStateException("The server refused the read"))
        }
        emitAll(follows.map(answer).distinctUntilChanged())
    }

    private data class Follow(val followerUid: String, val followedUid: String)
}
