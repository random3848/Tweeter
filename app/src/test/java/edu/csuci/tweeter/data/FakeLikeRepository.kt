package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * A [LikeRepository] for tests, holding the logged-in user's likes in memory, starting with
 * [initialLikedPostIds]. It leaves like counts alone, which the post repository holds.
 */
class FakeLikeRepository(initialLikedPostIds: Set<String> = emptySet()) : LikeRepository {
    private val likedPostIds = MutableStateFlow(initialLikedPostIds)

    override fun getLikedPostIds(): Flow<Set<String>> = likedPostIds

    override suspend fun like(postId: String) {
        likedPostIds.update { it + postId }
    }

    override suspend fun unlike(postId: String) {
        likedPostIds.update { it - postId }
    }
}
