package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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
     * The IDs of the users [followerUid] follows, updated as they follow and unfollow.
     *
     * A failed read fails the flow.
     */
    fun observeFollowedUids(followerUid: String): Flow<Set<String>>

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
 */
fun FollowRepository.observeFriendUids(userUid: String): Flow<Set<String>> =
    combine(observeFollowedUids(userUid), observeFollowerUids(userUid)) { followed, followers ->
        followed intersect followers
    }
