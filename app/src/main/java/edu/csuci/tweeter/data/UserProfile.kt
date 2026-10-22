package edu.csuci.tweeter.data

/**
 * What a user shows about themselves on their profile.
 *
 * Screens get profiles from a [ProfileRepository].
 *
 * @property uid The user's Firebase Authentication ID, which also identifies their posts
 *     ([Post.authorUid]).
 * @property displayName The name shown on the profile and on the user's new posts.
 * @property bio What the user wrote about themselves; may be empty.
 * @property photoUrl The profile photo's `https://` URL, or empty when the user has no photo.
 */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val bio: String,
    val photoUrl: String
) {
    /** The profile picture: the initials of [displayName]. */
    val avatar: Avatar
        get() = Avatar(initials = initialsOf(displayName))

    companion object {
        /** The name of a user who hasn't set one. */
        const val DEFAULT_DISPLAY_NAME = "Guest"

        /** Returns the profile of user [uid] before they have set anything up. */
        fun createDefault(uid: String): UserProfile = UserProfile(
            uid = uid,
            displayName = DEFAULT_DISPLAY_NAME,
            bio = "",
            photoUrl = ""
        )
    }
}
