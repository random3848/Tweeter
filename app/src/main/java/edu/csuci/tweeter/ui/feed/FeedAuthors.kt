package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.PostTable

/** Whose posts the feed shows, as the [FeedChoice] the user picked decides. */
sealed interface FeedAuthors {
    /**
     * The rows of [posts] the feed shows: those of [rankedRows], every row of [posts] in the order
     * the user picked, whose posts these authors wrote, in that order.
     */
    fun rowsIn(posts: PostTable, rankedRows: IntArray): IntArray

    /** Everyone: the feed shows every post. */
    data object Everyone : FeedAuthors {
        override fun rowsIn(posts: PostTable, rankedRows: IntArray): IntArray = rankedRows
    }

    /**
     * The user's friends: the feed shows their posts.
     *
     * @property uids The friends' IDs.
     */
    data class Friends(val uids: Set<String>) : FeedAuthors {
        override fun rowsIn(posts: PostTable, rankedRows: IntArray): IntArray =
            rankedRows.filter { row -> posts.authorUid(row) in uids }.toIntArray()
    }

    /** The user's friends, while they are read: the feed shows no posts yet. */
    data object LoadingFriends : FeedAuthors {
        override fun rowsIn(posts: PostTable, rankedRows: IntArray): IntArray = IntArray(0)
    }

    /** The user's friends, who couldn't be read: the feed shows no posts, and offers a retry. */
    data object UnknownFriends : FeedAuthors {
        override fun rowsIn(posts: PostTable, rankedRows: IntArray): IntArray = IntArray(0)
    }
}
