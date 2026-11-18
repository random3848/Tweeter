package edu.csuci.tweeter.data

import java.time.Instant

/**
 * What a logged-in user wrote under a post.
 *
 * @property id Unique among the post's comments.
 * @property authorUid The commenter's ID.
 * @property username The commenter's name when they wrote it.
 * @property text What they wrote, at most [MAX_LENGTH] characters.
 * @property timestamp When Firestore received it.
 */
data class Comment(
    val id: String,
    val authorUid: String,
    val username: String,
    val text: String,
    val timestamp: Instant
) {
    /** The limits on [text]. */
    companion object {
        /** The longest [text] can be; firestore.rules checks the same number. */
        const val MAX_LENGTH = 500
    }
}
