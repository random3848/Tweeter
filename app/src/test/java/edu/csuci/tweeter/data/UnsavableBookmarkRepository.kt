package edu.csuci.tweeter.data

/**
 * A [BookmarkRepository] for tests whose bookmarks can't be saved, as when the Firestore rules
 * refuse them.
 */
class UnsavableBookmarkRepository : BookmarkRepository by FakeBookmarkRepository() {
    /** How many times a bookmark was tried. */
    var attemptCount = 0
        private set

    override suspend fun bookmark(postId: String) {
        attemptCount++
        throw BookmarkException(IllegalStateException("Refused"))
    }
}
