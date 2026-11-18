package edu.csuci.tweeter.ui.friends

/**
 * A user's friends, as [FriendsReader] reads them: the users who follow them and whom they follow
 * back.
 */
sealed interface UserFriends {
    /**
     * The friends, as read.
     *
     * @property uids The friends' IDs.
     */
    data class Known(val uids: Set<String>) : UserFriends

    /** The friends, while they are read for the first time. */
    data object Loading : UserFriends

    /** The friends, who couldn't be read, until the user asks to retry. */
    data object Unknown : UserFriends
}
