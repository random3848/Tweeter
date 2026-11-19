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
    fun toggle_postNotSaved_savesIt() = runTest {
        val bookmarks = FakeBookmarkRepository()

        PostBookmarker(bookmarks).toggle(POST_ID, bookmarkedPostIds = emptySet())

        assertEquals(setOf(POST_ID), bookmarks.getBookmarkedPostIds().first())
    }

    @Test
    fun toggle_savedPost_unsavesIt() = runTest {
        val bookmarks = FakeBookmarkRepository(initialBookmarkedPostIds = setOf(POST_ID))

        PostBookmarker(bookmarks).toggle(POST_ID, bookmarkedPostIds = setOf(POST_ID))

        assertEquals(emptySet<String>(), bookmarks.getBookmarkedPostIds().first())
    }

    @Test
    fun toggle_bookmarkNotSaved_doesNotThrow() = runTest {
        val bookmarks = UnsavableBookmarkRepository()

        PostBookmarker(bookmarks).toggle(POST_ID, bookmarkedPostIds = emptySet())

        assertEquals(1, bookmarks.attemptCount)
    }

    @Test
    fun toggle_afterBookmarkNotSaved_triesAgain() = runTest {
        val bookmarks = UnsavableBookmarkRepository()
        val bookmarker = PostBookmarker(bookmarks)
        bookmarker.toggle(POST_ID, bookmarkedPostIds = emptySet())

        bookmarker.toggle(POST_ID, bookmarkedPostIds = emptySet())

        assertEquals(2, bookmarks.attemptCount)
    }
}
