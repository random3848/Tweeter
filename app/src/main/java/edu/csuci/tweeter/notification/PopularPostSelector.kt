package edu.csuci.tweeter.notification

import edu.csuci.tweeter.data.PostTable
import java.util.concurrent.TimeUnit.DAYS

// How many likes make a post popular. A user likes a post at most once, and the app's users are
// one campus's, not the world's: 10 of them liking a post is far more than most posts get, yet
// within reach of a good one.
private const val MIN_LIKES_OF_POPULAR_POST = 10

// How long after it is shared a post can become popular news. A few days keeps it about what is
// happening now (by then the feed's Best order ranks even a new post without likes above it), and
// bounds the posts each check remembers.
private val RECENCY_WINDOW_MILLIS = DAYS.toMillis(3)

/**
 * Chooses the posts a user hears of as popular: posts by someone else, shared less than
 * [windowMillis] ago, with at least [minLikeCount] likes, that they weren't notified of yet,
 * wherever the posts were shared.
 *
 * @property minLikeCount How many likes make a post popular, so that a check reads only the posts
 *     with at least that many.
 */
class PopularPostSelector(
    val minLikeCount: Int = MIN_LIKES_OF_POPULAR_POST,
    private val windowMillis: Long = RECENCY_WINDOW_MILLIS
) {
    /**
     * The time after which a post must have been shared to be recent at [nowMillis], in
     * milliseconds since the epoch: posts shared at it or before are too old to choose.
     */
    fun recentAfterMillis(nowMillis: Long): Long = nowMillis - windowMillis

    /** The rows of [posts] that [recipient] is to be notified of at [nowMillis], in table order. */
    fun rowsToNotify(posts: PostTable, nowMillis: Long, recipient: PopularPostRecipient): IntArray {
        val recentAfterMillis = recentAfterMillis(nowMillis)
        val rows = IntArray(posts.size)
        var rowCount = 0
        for (row in 0 until posts.size) {
            val isPopularNow = isRecentAndPopular(posts, row, recentAfterMillis)
            if (isPopularNow && isNews(posts, row, recipient)) {
                rows[rowCount] = row
                rowCount++
            }
        }
        return rows.copyOf(rowCount)
    }

    private fun isRecentAndPopular(posts: PostTable, row: Int, recentAfterMillis: Long): Boolean {
        val isRecent = posts.timestampMillis(row) > recentAfterMillis
        return isRecent && posts.likeCount(row) >= minLikeCount
    }

    // Whether the post in [row] is by someone else, and [recipient] wasn't notified of it yet.
    private fun isNews(posts: PostTable, row: Int, recipient: PopularPostRecipient): Boolean {
        val isOwnPost = posts.authorUid(row) == recipient.uid
        return !isOwnPost && posts.id(row) !in recipient.notifiedPostIds
    }
}
