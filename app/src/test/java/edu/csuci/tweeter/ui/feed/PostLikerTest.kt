@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.FakeLikeRepository
import edu.csuci.tweeter.data.SlowLikeRepository
import edu.csuci.tweeter.data.UnsavableLikeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val POST_ID = "post"
private const val OTHER_POST_ID = "other post"

class PostLikerTest {
    @Test
    fun toggle_postNotLiked_likesIt() = runTest {
        val likes = FakeLikeRepository()

        PostLiker(likes).toggle(POST_ID, likedPostIds = emptySet())

        assertEquals(setOf(POST_ID), likes.getLikedPostIds().first())
    }

    @Test
    fun toggle_likedPost_takesLikeBack() = runTest {
        val likes = FakeLikeRepository(initialLikedPostIds = setOf(POST_ID))

        PostLiker(likes).toggle(POST_ID, likedPostIds = setOf(POST_ID))

        assertEquals(emptySet<String>(), likes.getLikedPostIds().first())
    }

    @Test
    fun toggle_likeNotSaved_doesNotThrow() = runTest {
        val likes = UnsavableLikeRepository()

        PostLiker(likes).toggle(POST_ID, likedPostIds = emptySet())

        assertEquals(1, likes.attemptCount)
    }

    @Test
    fun toggle_tappedAgainBeforeSaved_likesOnce() = runTest {
        val likes = SlowLikeRepository()
        val liker = PostLiker(likes)

        startLike(liker, POST_ID)
        startLike(liker, POST_ID)

        assertEquals(1, likes.changeCount)
    }

    @Test
    fun toggle_afterLikeSaved_takesLikeBack() = runTest {
        val likes = SlowLikeRepository()
        val liker = PostLiker(likes)
        startLike(liker, POST_ID)
        likes.finishChanges()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            liker.toggle(POST_ID, likedPostIds = setOf(POST_ID))
        }
        likes.finishChanges()

        assertEquals(emptySet<String>(), likes.getLikedPostIds().first())
    }

    @Test
    fun toggle_afterLikeNotSaved_triesAgain() = runTest {
        val likes = UnsavableLikeRepository()
        val liker = PostLiker(likes)
        liker.toggle(POST_ID, likedPostIds = emptySet())

        liker.toggle(POST_ID, likedPostIds = emptySet())

        assertEquals(2, likes.attemptCount)
    }

    @Test
    fun toggle_otherPostWhileOneIsSaving_changesBoth() = runTest {
        val likes = SlowLikeRepository()
        val liker = PostLiker(likes)

        startLike(liker, POST_ID)
        startLike(liker, OTHER_POST_ID)

        assertEquals(2, likes.changeCount)
    }

    // Starts liking the post, which waits for the slow repository until the test finishes it.
    private fun TestScope.startLike(liker: PostLiker, postId: String) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            liker.toggle(postId, likedPostIds = emptySet())
        }
    }
}
