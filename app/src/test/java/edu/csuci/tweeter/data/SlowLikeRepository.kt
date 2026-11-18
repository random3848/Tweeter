package edu.csuci.tweeter.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow

/**
 * A [LikeRepository] for tests whose likes and unlikes aren't saved until the test calls
 * [finishChanges], as on a slow network.
 */
class SlowLikeRepository : LikeRepository {
    private val saved = FakeLikeRepository()
    private var pendingNetwork = CompletableDeferred<Unit>()

    /** How many likes and unlikes were started, saved or not. */
    var changeCount = 0
        private set

    override fun getLikedPostIds(): Flow<Set<String>> = saved.getLikedPostIds()

    override suspend fun like(postId: String) {
        awaitNetwork()
        saved.like(postId)
    }

    override suspend fun unlike(postId: String) {
        awaitNetwork()
        saved.unlike(postId)
    }

    /** Saves every change started so far. */
    fun finishChanges() {
        val finished = pendingNetwork
        pendingNetwork = CompletableDeferred()
        finished.complete(Unit)
    }

    private suspend fun awaitNetwork() {
        changeCount++
        pendingNetwork.await()
    }
}
