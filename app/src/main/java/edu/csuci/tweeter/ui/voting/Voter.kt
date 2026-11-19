package edu.csuci.tweeter.ui.voting

/** Who is looking at the votes on a post's animal, which decides what their vote controls do. */
enum class Voter {
    /** Someone not logged in, whom a vote control sends to Log in. */
    GUEST,

    /** The user who shared the post, who can't vote on it but can reclassify it. */
    POSTER,

    /** Any other logged-in user, who can vote. */
    USER;

    /** Finds [Voter]s. */
    companion object {
        /**
         * Returns who user [loggedInUid], or a guest when it's null, is on a post shared by
         * [authorUid], which is empty for a post shared while logged out.
         */
        fun of(loggedInUid: String?, authorUid: String): Voter = when (loggedInUid) {
            null -> GUEST
            authorUid -> POSTER
            else -> USER
        }
    }
}
