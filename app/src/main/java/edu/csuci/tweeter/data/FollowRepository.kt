package edu.csuci.tweeter.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Follower relationships between registered users.
 *
 * Following is directional: following someone doesn't make them follow you. Two users who follow
 * each other are friends (see [observeFriendUids]).
 * Screens use this interface through their ViewModels.
 */
interface FollowRepository {
    /**
     * Whether [followerUid] follows [followedUid], updated when the relationship changes.
     *
     * A missing relationship emits false. A failed read fails the flow.
     */
    fun observeFollowing(followerUid: String, followedUid: String): Flow<Boolean>

    /**
     * The number of followers of [userUid], updated when relationships change.
     *
     * Waits for server-backed data before emitting. A failed read fails the flow.
     */
    fun observeFollowerCount(userUid: String): Flow<Int>

    /**
     * The number of users [userUid] follows, updated when relationships change.
     *
     * Waits for server-backed data before emitting. A failed read fails the flow.
     */
    fun observeFollowingCount(userUid: String): Flow<Int>

    /**
     * The IDs of the users who follow [followedUid], updated as they follow and unfollow.
     *
     * A failed read fails the flow.
     */
    fun observeFollowerUids(followedUid: String): Flow<Set<String>>

    /**
     * Makes the logged-in user a follower of [userUid].
     *
     * Following an already-followed user leaves the relationship unchanged.
     * Guests and users attempting to follow themselves are rejected.
     */
    suspend fun follow(userUid: String)

    /**
     * Removes the logged-in user's following relationship with [userUid].
     *
     * Unfollowing someone they don't follow leaves the relationship unchanged.
     * Guests cannot change follower relationships.
     */
    suspend fun unfollow(userUid: String)
}

/**
 * The IDs of [userUid]'s friends: the users who follow [userUid] and whom [userUid] follows back,
 * updated as either of them follows or unfollows. A failed read fails the flow.
 *
 * It reads only followers, which every follow is stored as: [userUid]'s followers, then, for each
 * of them, whether [userUid] is among theirs. That's one listener for [userUid]'s followers and
 * one per follower.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun FollowRepository.observeFriendUids(userUid: String): Flow<Set<String>> =
    observeFollowerUids(userUid).flatMapLatest { followerUids ->
        if (followerUids.isEmpty()) {
            flowOf(emptySet())
        } else {
            val followedBack = followerUids.map { followerUid ->
                observeFollowing(userUid, followerUid)
                    .map { follows -> followerUid.takeIf { follows } }
            }
            combine(followedBack) { friendUids -> friendUids.filterNotNullTo(HashSet()) }
        }
    }
