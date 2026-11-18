package edu.csuci.tweeter.data

/**
 * A [LikeRepository] for tests whose likes can't be saved, as when the Firestore rules refuse
 * them.
 */
class UnsavableLikeRepository : LikeRepository by FakeLikeRepository() {
    /** How many times a like was tried. */
    var attemptCount = 0
        private set

    override suspend fun like(postId: String) {
        attemptCount++
        throw LikeException(IllegalStateException("Refused"))
    }
}
