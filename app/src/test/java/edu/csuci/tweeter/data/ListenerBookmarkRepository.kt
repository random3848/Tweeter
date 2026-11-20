package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A [BookmarkRepository] for tests whose saved posts arrive as a Firestore listener's do: only when
 * [deliver] delivers them, so never if it isn't called, as when the rules refuse the listener, and
 * each time it is, even when they equal the last. Bookmarks saved through it don't arrive.
 */
class ListenerBookmarkRepository : BookmarkRepository by FakeBookmarkRepository() {
    private val deliveries = MutableSharedFlow<Set<String>>(replay = 1)

    override fun getBookmarkedPostIds(): Flow<Set<String>> = deliveries

    /** Sends [bookmarkedPostIds] to everyone reading the saved posts, and to those who start to. */
    suspend fun deliver(bookmarkedPostIds: Set<String>) {
        deliveries.emit(bookmarkedPostIds)
    }
}
