package edu.csuci.tweeter.ui.map

import edu.csuci.tweeter.data.PostTable

/** Whose posts the map pins, as the filter shown selected above it picks them. */
sealed interface PinnedAuthors {
    /**
     * The filter that picked these authors, shown selected, or null while nobody is logged in and
     * the map offers no filter.
     */
    val filter: PinFilter?

    /** The rows of [posts] holding the posts the map pins, in table order. */
    fun rowsIn(posts: PostTable): IntArray

    /**
     * The authors to pin when these arrive while [pinned] are pinned: these, unless they are still
     * being read and [pinned] can stand in for them meanwhile.
     */
    fun replacing(pinned: PinnedAuthors): PinnedAuthors = this

    /** Everyone, for a guest: the map pins every post and offers no filter. */
    data object EveryoneForGuests : PinnedAuthors {
        override val filter: PinFilter? = null

        override fun rowsIn(posts: PostTable): IntArray = everyRowOf(posts)
    }

    /** Everyone, as [PinFilter.ALL] picks: the map pins every post. */
    data object Everyone : PinnedAuthors {
        override val filter: PinFilter = PinFilter.ALL

        override fun rowsIn(posts: PostTable): IntArray = everyRowOf(posts)
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
     * A user's friends, as [PinFilter.FRIENDS] picks: the map pins their posts.
     *
     * @property userUid The user whose friends they are.
     * @property uids The friends' IDs.
     */
    data class Friends(val userUid: String, val uids: Set<String>) : PinnedAuthors {
        override val filter: PinFilter = PinFilter.FRIENDS

        override fun rowsIn(posts: PostTable): IntArray = posts.rowsBy(uids)
    }

    /**
     * A user's friends, while they are read: the map pins no posts yet.
     *
     * @property userUid The user whose friends are read.
     */
    data class LoadingFriends(val userUid: String) : PinnedAuthors {
        override val filter: PinFilter = PinFilter.FRIENDS

        override fun rowsIn(posts: PostTable): IntArray = IntArray(0)

        // The same user's friends already pinned, as when Map is shown again, stay pinned while
        // they're read again. Another user's, as after logging in as someone else, don't.
        override fun replacing(pinned: PinnedAuthors): PinnedAuthors =
            if (pinned is Friends && pinned.userUid == userUid) pinned else this
    }

    /** The user's friends, who couldn't be read: the map pins no posts. */
    data object UnknownFriends : PinnedAuthors {
        override val filter: PinFilter = PinFilter.FRIENDS

        override fun rowsIn(posts: PostTable): IntArray = IntArray(0)
    }
}

private fun everyRowOf(posts: PostTable): IntArray = IntArray(posts.size) { row -> row }
