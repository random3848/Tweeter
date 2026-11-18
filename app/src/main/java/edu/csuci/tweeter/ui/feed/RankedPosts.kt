package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.PostRows
import edu.csuci.tweeter.data.PostTable

/**
 * The feed's posts in the [order] the user picked, ranked once when they picked it. Newer tables
 * keep that ranking ([withPosts]), so a post doesn't move while the user looks at it, as when they
 * like it.
 *
 * @property listedPosts Every post, in the order the feed shows them.
 * @property order The order the user picked, which [listedPosts] are in.
 */
class RankedPosts private constructor(
    val listedPosts: PostRows,
    val order: FeedOrder,
    private val rankByPostId: Map<String, Int>
) {
    /**
     * [newPosts] in this ranking: posts it ranked keep their order, posts shared since come first,
     * newest first, and deleted posts are dropped.
     */
    fun withPosts(newPosts: PostTable): RankedPosts {
        val rows = newPosts.rowsByDescending { row -> scoreOf(newPosts.id(row)) }
        return RankedPosts(PostRows(newPosts, rows), order, rankByPostId)
    }

    /** Where in [listedPosts] the post with [postId] is, or null if they don't hold it. */
    fun positionOf(postId: String): Int? =
        listedPosts.table.rowOf(postId)?.let(listedPosts.rows::indexOf)

    // Higher for posts ranked higher, and highest for posts shared after the ranking.
    private fun scoreOf(postId: String): Double =
        rankByPostId[postId]?.let { rank -> -rank.toDouble() } ?: Double.POSITIVE_INFINITY

    companion object {
        /**
         * Ranks [posts] in [order] now.
         *
         * @param nowMillis The time it is, in milliseconds since the epoch, which ages the posts.
         */
        fun of(posts: PostTable, order: FeedOrder, nowMillis: Long): RankedPosts {
            val rows = order.rowsOf(posts, nowMillis)
            val rankByPostId = HashMap<String, Int>(rows.size)
            rows.forEachIndexed { rank, row -> rankByPostId[posts.id(row)] = rank }
            return RankedPosts(PostRows(posts, rows), order, rankByPostId)
        }
    }
}
