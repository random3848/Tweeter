package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.PostRows

/** Whose posts the feed shows, as the [FeedChoice] the user picked decides. */
sealed interface FeedAuthors {
    /**
     * The posts the feed shows: those of [ranked], every post in the order the user picked, that
     * these authors wrote, in that order.
     */
    fun listedIn(ranked: PostRows): PostRows

    /**
     * Whether the feed leaves out the posts the user with [authorUid] wrote, as far as it knows
     * yet: while the followed users are read, it may still show them.
     */
    fun leavesOut(authorUid: String): Boolean

    /** Everyone: the feed shows every post. */
    data object Everyone : FeedAuthors {
        override fun listedIn(ranked: PostRows): PostRows = ranked

        override fun leavesOut(authorUid: String): Boolean = false
    }

    /**
     * The users the user follows: the feed shows their posts.
     *
     * @property uids The followed users' IDs.
     */
    data class Followed(val uids: Set<String>) : FeedAuthors {
        override fun listedIn(ranked: PostRows): PostRows {
            val posts = ranked.table
            val rows = ranked.rows.filter { row -> posts.authorUid(row) in uids }.toIntArray()
            return PostRows(posts, rows)
        }

        override fun leavesOut(authorUid: String): Boolean = authorUid !in uids
    }

    /**
     * The users the user follows, while they are first read: the feed shows no posts yet. When
     * they are read again, as when Feed is shown again, those read last are shown meanwhile
     * instead.
     */
    data object LoadingFollowed : FeedAuthors {
        override fun listedIn(ranked: PostRows): PostRows = PostRows(ranked.table, IntArray(0))

        override fun leavesOut(authorUid: String): Boolean = false
    }

    /**
     * The users the user follows, who couldn't be read: the feed shows no posts, and offers a
     * retry.
     */
    data object UnknownFollowed : FeedAuthors {
        override fun listedIn(ranked: PostRows): PostRows = PostRows(ranked.table, IntArray(0))

        override fun leavesOut(authorUid: String): Boolean = true
    }
}
