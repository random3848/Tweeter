package edu.csuci.tweeter.ui.feed

import androidx.annotation.StringRes
import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.PostTable

/**
 * An order the feed can show its posts in, which the user picks above the posts.
 *
 * @property labelRes The name of the order on its chip.
 */
enum class FeedOrder(@StringRes val labelRes: Int) {
    /** Recent posts first, lifted by their likes, ranked by [bestScoreOf]. */
    BEST(R.string.feed_order_best) {
        override fun rowsOf(posts: PostTable, nowMillis: Long): IntArray =
            posts.rowsByDescending { row ->
                bestScoreOf(posts.likeCount(row), nowMillis - posts.timestampMillis(row))
            }
    },

    /** The newest posts first, the order the table holds them in. */
    NEWEST(R.string.feed_order_newest) {
        override fun rowsOf(posts: PostTable, nowMillis: Long): IntArray =
            IntArray(posts.size) { row -> row }
    },

    /** The most liked posts first, however old. */
    TOP(R.string.feed_order_top) {
        override fun rowsOf(posts: PostTable, nowMillis: Long): IntArray = posts.rowsByLikeCount()
    };

    /**
     * Every row of [posts], in the order the feed shows them. It can be a pass over every post.
     *
     * @param nowMillis The time it is, in milliseconds since the epoch, which ages the posts.
     */
    abstract fun rowsOf(posts: PostTable, nowMillis: Long): IntArray
}
