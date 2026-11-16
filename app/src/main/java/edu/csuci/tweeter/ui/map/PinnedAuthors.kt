package edu.csuci.tweeter.ui.map

import edu.csuci.tweeter.data.PostTable

/** Whose posts the map pins, as the user's [PinFilter] picks them. */
sealed interface PinnedAuthors {
    /** The rows of [posts] holding the posts the map pins, in table order. */
    fun rowsIn(posts: PostTable): IntArray

    /** Everyone: the map pins every post. */
    data object Everyone : PinnedAuthors {
        override fun rowsIn(posts: PostTable): IntArray = IntArray(posts.size) { row -> row }
    }

    /**
     * Some users: the map pins their posts alone.
     *
     * @property uids The users whose posts the map pins.
     */
    data class Only(val uids: Set<String>) : PinnedAuthors {
        override fun rowsIn(posts: PostTable): IntArray = posts.rowsBy(uids)
    }

    /** The user's friends, who couldn't be read: the map pins no posts. */
    data object FriendsUnknown : PinnedAuthors {
        override fun rowsIn(posts: PostTable): IntArray = IntArray(0)
    }
}
