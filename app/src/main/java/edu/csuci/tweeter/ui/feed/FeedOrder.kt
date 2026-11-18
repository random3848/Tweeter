package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.PostTable

/** An order the feed can show its posts in, as the [FeedChoice] the user picks puts them. */
enum class FeedOrder {
    /** Recent posts with likes first, ranked by [bestScoreOf]. */
    BEST {
        override fun rowsOf(posts: PostTable, nowMillis: Long): IntArray =
            posts.rowsByDescending { row ->
                bestScoreOf(posts.likeCount(row), nowMillis - posts.timestampMillis(row))
            }
    },

    /** The newest posts first, the order the table holds them in. */
    NEWEST {
        override fun rowsOf(posts: PostTable, nowMillis: Long): IntArray =
            IntArray(posts.size) { row -> row }
    },

    /** The most liked posts first, however old. */
    TOP {
        override fun rowsOf(posts: PostTable, nowMillis: Long): IntArray = posts.rowsByLikeCount()
    };

    /**
     * Every row of [posts], in the order the feed shows them. It can be a pass over every post.
     *
     * @param nowMillis The time it is, in milliseconds since the epoch, which ages the posts.
     */
    abstract fun rowsOf(posts: PostTable, nowMillis: Long): IntArray
}
