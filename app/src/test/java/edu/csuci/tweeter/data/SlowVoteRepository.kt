package edu.csuci.tweeter.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow

/**
 * A [VoteRepository] for tests whose vote changes and reclassifications aren't saved until the
 * test calls [finishChanges], as on a slow network.
 */
class SlowVoteRepository : VoteRepository {
    private val saved = FakeVoteRepository()
    private var pendingNetwork = CompletableDeferred<Unit>()

    /** How many vote changes and reclassifications were started, saved or not. */
    var changeCount = 0
        private set

    override fun getVotes(): Flow<Map<String, AnimalVote>> = saved.getVotes()

    override fun getVoteCounts(postId: String): Flow<Map<String, Int>> = saved.getVoteCounts(postId)

    override suspend fun changeVote(change: VoteChange) {
        awaitNetwork()
        saved.changeVote(change)
    }

    override suspend fun reclassify(postId: String, animal: String) {
        awaitNetwork()
        saved.reclassify(postId, animal)
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
