package edu.csuci.tweeter.ui.map

import edu.csuci.tweeter.data.PostTable

/** Whose posts the map pins, as the filter shown selected above it picks them. */
sealed interface PinnedAuthors {
    /** The filter that picks these authors, shown selected while the map offers a filter. */
    val filter: PinFilter

    /** The rows of [posts] holding the posts the map pins, in table order. */
    fun rowsIn(posts: PostTable): IntArray

    /** Everyone, as [PinFilter.ALL] picks, and for guests, who get no filter: every post. */
    data object Everyone : PinnedAuthors {
        override val filter: PinFilter = PinFilter.ALL

        override fun rowsIn(posts: PostTable): IntArray = IntArray(posts.size) { row -> row }
    }

    /**
     * The user alone, as [PinFilter.MINE] picks: the map pins their own posts.
     *
     * @property uid The user's ID.
     */
    data class User(val uid: String) : PinnedAuthors {
        override val filter: PinFilter = PinFilter.MINE

        override fun rowsIn(posts: PostTable): IntArray = posts.rowsBy(uid)
    }

    /**
     * The user's friends, as [PinFilter.FRIENDS] picks: the map pins their posts.
     *
     * @property uids The friends' IDs.
     */
    data class Friends(val uids: Set<String>) : PinnedAuthors {
        override val filter: PinFilter = PinFilter.FRIENDS

        override fun rowsIn(posts: PostTable): IntArray = posts.rowsBy(uids)
    }

    /**
     * The user's friends, while they are first read: the map pins no posts yet. When they are read
     * again, as when Map is shown again, the friends read last stay pinned meanwhile instead.
     */
    data object LoadingFriends : PinnedAuthors {
        override val filter: PinFilter = PinFilter.FRIENDS

        override fun rowsIn(posts: PostTable): IntArray = IntArray(0)
    }

    /** The user's friends, who couldn't be read: the map pins no posts. */
    data object UnknownFriends : PinnedAuthors {
        override val filter: PinFilter = PinFilter.FRIENDS

        override fun rowsIn(posts: PostTable): IntArray = IntArray(0)
    }
}
