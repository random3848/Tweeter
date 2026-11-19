package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.FakeBookmarkRepository
import edu.csuci.tweeter.data.UnsavableBookmarkRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val POST_ID = "post"

class PostBookmarkerTest {
    @Test
    fun bookmarkedPostIds_areTheRepositorys() = runTest {
        val bookmarks = FakeBookmarkRepository(initialBookmarkedPostIds = setOf(POST_ID))

        assertEquals(setOf(POST_ID), PostBookmarker(bookmarks).bookmarkedPostIds().first())
    }

    @Test
    fun bookmark_savesIt() = runTest {
        val bookmarks = FakeBookmarkRepository()

        PostBookmarker(bookmarks).bookmark(POST_ID)

        assertEquals(setOf(POST_ID), bookmarks.getBookmarkedPostIds().first())
    }

    @Test
    fun unbookmark_removesIt() = runTest {
        val bookmarks = FakeBookmarkRepository(initialBookmarkedPostIds = setOf(POST_ID))

        PostBookmarker(bookmarks).unbookmark(POST_ID)

        assertEquals(emptySet<String>(), bookmarks.getBookmarkedPostIds().first())
    }

    @Test
    fun bookmark_notSaved_doesNotThrow() = runTest {
        val bookmarks = UnsavableBookmarkRepository()

        PostBookmarker(bookmarks).bookmark(POST_ID)

        assertEquals(1, bookmarks.attemptCount)
    }

    @Test
    fun bookmark_afterBookmarkNotSaved_triesAgain() = runTest {
        val bookmarks = UnsavableBookmarkRepository()
        val bookmarker = PostBookmarker(bookmarks)
        bookmarker.bookmark(POST_ID)

        bookmarker.bookmark(POST_ID)

        assertEquals(2, bookmarks.attemptCount)
    }
}
