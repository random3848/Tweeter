package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * A [BookmarkRepository] for tests, holding the logged-in user's saved posts in memory, starting
 * with [initialBookmarkedPostIds].
 */
class FakeBookmarkRepository(initialBookmarkedPostIds: Set<String> = emptySet()) :
    BookmarkRepository {
    private val bookmarkedPostIds = MutableStateFlow(initialBookmarkedPostIds)

    override fun getBookmarkedPostIds(): Flow<Set<String>> = bookmarkedPostIds

    override suspend fun bookmark(postId: String) {
        bookmarkedPostIds.update { it + postId }
    }

    override suspend fun unbookmark(postId: String) {
        bookmarkedPostIds.update { it - postId }
    }
}
