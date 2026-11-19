package edu.csuci.tweeter.notification

import edu.csuci.tweeter.data.FakeNumberStore
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val CHECK_NAME = "popularPosts"
private const val OTHER_CHECK_NAME = "otherPosts"
private const val SHARED_MILLIS = 1_700_000_000_000L

class NotifiedPostStoreTest {
    private val numbers = FakeNumberStore()

    @Test
    fun notifiedPostIds_nothingRemembered_isEmpty() = runTest {
        assertEquals(emptySet<String>(), NotifiedPostStore(numbers, CHECK_NAME).notifiedPostIds())
    }

    @Test
    fun remember_remembersOnlyChosenRows() = runTest {
        val posts = tableOf(post("first"), post("second"), post("third"))

        NotifiedPostStore(numbers, CHECK_NAME).remember(posts, rows = intArrayOf(0, 2))

        assertNotified("first", "third")
    }

    @Test
    fun remember_thenNewStoreOverSameNumbers_knowsPosts() = runTest {
        NotifiedPostStore(numbers, CHECK_NAME).remember(tableOf(post("first")), intArrayOf(0))

        val afterRestart = NotifiedPostStore(numbers, CHECK_NAME)

        assertEquals(setOf("first"), afterRestart.notifiedPostIds())
    }

    @Test
    fun remember_oneCheck_leavesOtherChecksAlone() = runTest {
        NotifiedPostStore(numbers, CHECK_NAME).remember(tableOf(post("first")), intArrayOf(0))

        val otherCheckPosts = NotifiedPostStore(numbers, OTHER_CHECK_NAME).notifiedPostIds()

        assertEquals(emptySet<String>(), otherCheckPosts)
    }

    @Test
    fun forgetPostsSharedUpTo_postSharedAtThatTime_forgetsIt() = runTest {
        val store = storeRemembering(post("at", sharedMillis = SHARED_MILLIS))

        store.forgetPostsSharedUpTo(SHARED_MILLIS)

        assertNotified()
    }

    @Test
    fun forgetPostsSharedUpTo_postSharedBefore_forgetsIt() = runTest {
        val store = storeRemembering(post("before", sharedMillis = SHARED_MILLIS - 1))

        store.forgetPostsSharedUpTo(SHARED_MILLIS)

        assertNotified()
    }

    @Test
    fun forgetPostsSharedUpTo_postSharedJustAfter_keepsIt() = runTest {
        val store = storeRemembering(post("after", sharedMillis = SHARED_MILLIS + 1))

        store.forgetPostsSharedUpTo(SHARED_MILLIS)

        assertNotified("after")
    }

    @Test
    fun forgetPostsSharedUpTo_severalPosts_forgetsOnlyOlderOnes() = runTest {
        val store = storeRemembering(
            post("old", sharedMillis = SHARED_MILLIS - 1),
            post("new", sharedMillis = SHARED_MILLIS + 1)
        )

        store.forgetPostsSharedUpTo(SHARED_MILLIS)

        assertNotified("new")
    }

    @Test
    fun forgetPostsSharedUpTo_oneCheck_leavesOtherChecksAlone() = runTest {
        val oldPost = tableOf(post("old", sharedMillis = SHARED_MILLIS - 1))
        NotifiedPostStore(numbers, OTHER_CHECK_NAME).remember(oldPost, intArrayOf(0))

        NotifiedPostStore(numbers, CHECK_NAME).forgetPostsSharedUpTo(SHARED_MILLIS)

        assertEquals(setOf("old"), NotifiedPostStore(numbers, OTHER_CHECK_NAME).notifiedPostIds())
    }

    private suspend fun storeRemembering(vararg posts: Post): NotifiedPostStore {
        val store = NotifiedPostStore(numbers, CHECK_NAME)
        store.remember(tableOf(*posts), rows = IntArray(posts.size) { row -> row })
        return store
    }

    private suspend fun assertNotified(vararg postIds: String) {
        assertEquals(postIds.toSet(), NotifiedPostStore(numbers, CHECK_NAME).notifiedPostIds())
    }

    private fun tableOf(vararg posts: Post): PostTable = PostTable.of(posts.toList())

    private fun post(id: String, sharedMillis: Long = SHARED_MILLIS): Post =
        SAMPLE_POSTS.first().copy(id = id, timestamp = Instant.ofEpochMilli(sharedMillis))
}
