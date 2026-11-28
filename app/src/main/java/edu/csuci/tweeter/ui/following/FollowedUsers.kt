package edu.csuci.tweeter.ui.following

/** The users someone follows, as [FollowedUsersReader] reads them. */
sealed interface FollowedUsers {
    /**
     * The followed users, as read.
     *
     * @property uids The followed users' IDs.
     */
    data class Known(val uids: Set<String>) : FollowedUsers

    /** The followed users, while they are read for the first time. */
    data object Loading : FollowedUsers

    /** The followed users, who couldn't be read, until the user asks to retry. */
    data object Unknown : FollowedUsers
}
