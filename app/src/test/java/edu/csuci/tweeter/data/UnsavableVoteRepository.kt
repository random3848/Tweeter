package edu.csuci.tweeter.data

/**
 * A [VoteRepository] for tests whose vote changes and reclassifications can't be saved, as when
 * the Firestore rules refuse them.
 */
class UnsavableVoteRepository : VoteRepository by FakeVoteRepository() {
    /** How many times a vote change or reclassification was tried. */
    var attemptCount = 0
        private set

    override suspend fun changeVote(change: VoteChange) {
        refuse()
    }

    override suspend fun reclassify(postId: String, animal: String) {
        refuse()
    }

    private fun refuse() {
        attemptCount++
        throw VoteException(IllegalStateException("Refused"))
    }
}
