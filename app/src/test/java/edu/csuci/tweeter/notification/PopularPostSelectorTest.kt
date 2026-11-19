package edu.csuci.tweeter.notification

import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import java.time.Instant
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.HOURS
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

private const val USER_UID = "maya"
private const val OTHER_UID = "jordan"
private const val MIN_LIKES = 10
private const val NOW_MILLIS = 1_700_000_000_000L

private val WINDOW_MILLIS = DAYS.toMillis(3)
private val HOUR_MILLIS = HOURS.toMillis(1)

// The first moment a post is too old to be recent.
private val WINDOW_START_MILLIS = NOW_MILLIS - WINDOW_MILLIS

class PopularPostSelectorTest {
    private val selector =
        PopularPostSelector(minLikeCount = MIN_LIKES, windowMillis = WINDOW_MILLIS)
    private val recipient = PopularPostRecipient(uid = USER_UID, notifiedPostIds = emptySet())

    @Test
    fun recentAfterMillis_isWindowBeforeNow() {
        assertEquals(WINDOW_START_MILLIS, selector.recentAfterMillis(NOW_MILLIS))
    }

    @Test
    fun rowsToNotify_popularRecentPostByOtherUser_selectsIt() {
        assertSelected(post(id = "popular"))
    }

    @Test
    fun rowsToNotify_exactlyMinLikes_selectsIt() {
        assertSelected(post(id = "threshold", likeCount = MIN_LIKES))
    }

    @Test
    fun rowsToNotify_oneLikeShortOfMin_selectsNothing() {
        assertNothingSelected(post(id = "almost", likeCount = MIN_LIKES - 1))
    }

    @Test
    fun rowsToNotify_noLikes_selectsNothing() {
        assertNothingSelected(post(id = "unliked", likeCount = 0))
    }

    @Test
    fun rowsToNotify_sharedJustAfterWindowStart_selectsIt() {
        assertSelected(post(id = "last-moment", sharedMillis = WINDOW_START_MILLIS + 1))
    }

    @Test
    fun rowsToNotify_sharedExactlyWindowAgo_selectsNothing() {
        assertNothingSelected(post(id = "expired", sharedMillis = WINDOW_START_MILLIS))
    }

    @Test
    fun rowsToNotify_sharedLongBeforeWindow_selectsNothing() {
        assertNothingSelected(post(id = "old", sharedMillis = WINDOW_START_MILLIS - HOUR_MILLIS))
    }

    // The server dates posts, so a phone whose clock is behind sees them shared in the future.
    @Test
    fun rowsToNotify_sharedAfterNow_selectsIt() {
        assertSelected(post(id = "ahead", sharedMillis = NOW_MILLIS + HOUR_MILLIS))
    }

    @Test
    fun rowsToNotify_ownPost_selectsNothing() {
        assertNothingSelected(post(id = "mine", authorUid = USER_UID))
    }

    @Test
    fun rowsToNotify_alreadyNotified_selectsNothing() {
        val notifiedRecipient = recipient.copy(notifiedPostIds = setOf("seen"))
        val posts = tableOf(post(id = "seen"))

        assertArrayEquals(
            intArrayOf(),
            selector.rowsToNotify(posts, NOW_MILLIS, notifiedRecipient)
        )
    }

    @Test
    fun rowsToNotify_postsAnywhere_selectsThemAll() {
        val posts = tableOf(
            post(id = "north").copy(latitude = 80.0, longitude = 0.0),
            post(id = "south").copy(latitude = -80.0, longitude = 179.0)
        )

        assertArrayEquals(intArrayOf(0, 1), selector.rowsToNotify(posts, NOW_MILLIS, recipient))
    }

    @Test
    fun rowsToNotify_severalPosts_selectsQualifyingRowsInTableOrder() {
        val notifiedRecipient = recipient.copy(notifiedPostIds = setOf("seen"))
        val posts = tableOf(
            post(id = "popular-1"),
            post(id = "almost", likeCount = MIN_LIKES - 1),
            post(id = "mine", authorUid = USER_UID),
            post(id = "seen"),
            post(id = "old", sharedMillis = WINDOW_START_MILLIS),
            post(id = "popular-2")
        )

        assertArrayEquals(
            intArrayOf(0, 5),
            selector.rowsToNotify(posts, NOW_MILLIS, notifiedRecipient)
        )
    }

    @Test
    fun rowsToNotify_noPosts_selectsNothing() {
        assertArrayEquals(
            intArrayOf(),
            selector.rowsToNotify(PostTable.EMPTY, NOW_MILLIS, recipient)
        )
    }

    private fun assertSelected(post: Post) {
        assertArrayEquals(
            intArrayOf(0),
            selector.rowsToNotify(tableOf(post), NOW_MILLIS, recipient)
        )
    }

    private fun assertNothingSelected(post: Post) {
        assertArrayEquals(intArrayOf(), selector.rowsToNotify(tableOf(post), NOW_MILLIS, recipient))
    }

    private fun tableOf(vararg posts: Post): PostTable = PostTable.of(posts.toList())

    private fun post(
        id: String,
        likeCount: Int = MIN_LIKES,
        authorUid: String = OTHER_UID,
        sharedMillis: Long = NOW_MILLIS - HOUR_MILLIS
    ): Post = SAMPLE_POSTS.first().copy(
        id = id,
        authorUid = authorUid,
        likeCount = likeCount,
        timestamp = Instant.ofEpochMilli(sharedMillis)
    )
}
