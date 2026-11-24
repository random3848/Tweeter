package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow

/**
 * Follower relationships between registered users.
 *
 * Following is directional: following someone doesn't make them follow you.
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
     * The IDs of the users [followerUid] follows, updated as they follow and unfollow.
     *
     * Unlike the counts, it emits the users the phone has cached first, then the server's, so it
     * works offline and shows a follow made on this phone at once. A failed read fails the flow.
     */
    fun observeFollowedUids(followerUid: String): Flow<Set<String>>

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
