package edu.csuci.tweeter.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

/**
 * How a [Post] is stored as a Firestore document in the [COLLECTION] collection.
 *
 * The document ID is [Post.id]. [Post.isLiked] and [Post.isBookmarked] depend on who is looking,
 * so they aren't stored and read back as false. [Post.timestamp] isn't stored either: Firestore
 * sets [TIMESTAMP] to when it receives the post, so a phone with a wrong clock can't misdate it.
 */
object PostDocument {
    /** The collection every post is stored in. */
    const val COLLECTION = "posts"

    /** The field posts are ordered by: when the post was shared. */
    const val TIMESTAMP = "timestamp"

    private const val USERNAME = "username"
    private const val AVATAR_INITIALS = "avatarInitials"
    private const val LOCATION_NAME = "locationName"
    private const val LATITUDE = "latitude"
    private const val LONGITUDE = "longitude"
    private const val IMAGE_URI = "imageUri"
    private const val CAPTION = "caption"
    private const val LIKE_COUNT = "likeCount"
    private const val COMMENT_COUNT = "commentCount"

    /** Returns the fields to store for [post], with [TIMESTAMP] left for Firestore to set. */
    fun fieldsOf(post: Post): Map<String, Any> = mapOf(
        USERNAME to post.username,
        AVATAR_INITIALS to post.avatar.initials,
        LOCATION_NAME to post.locationName,
        LATITUDE to post.latitude,
        LONGITUDE to post.longitude,
        IMAGE_URI to post.imageUri,
        CAPTION to post.caption,
        LIKE_COUNT to post.likeCount,
        COMMENT_COUNT to post.commentCount,
        TIMESTAMP to FieldValue.serverTimestamp()
    )

    /**
     * Returns the post stored in document [id] with [fields], or null if a required field is
     * missing or has the wrong type.
     *
     * The text fields that may be empty and the counts default when missing, so a post added by
     * hand in the Firebase console only needs [USERNAME], [LATITUDE], [LONGITUDE], [IMAGE_URI]
     * and [TIMESTAMP].
     */
    fun toPost(id: String, fields: Map<String, Any?>): Post? {
        val username = fields[USERNAME] as? String ?: return null
        // Firestore returns a whole number typed in the console as a Long.
        val latitude = (fields[LATITUDE] as? Number)?.toDouble() ?: return null
        val longitude = (fields[LONGITUDE] as? Number)?.toDouble() ?: return null
        val imageUri = fields[IMAGE_URI] as? String ?: return null
        val timestamp = fields[TIMESTAMP] as? Timestamp ?: return null

        return Post(
            id = id,
            username = username,
            avatar = Avatar(
                initials = fields[AVATAR_INITIALS] as? String ?: username.take(1).uppercase()
            ),
            locationName = fields[LOCATION_NAME] as? String ?: "",
            latitude = latitude,
            longitude = longitude,
            imageUri = imageUri,
            caption = fields[CAPTION] as? String ?: "",
            likeCount = (fields[LIKE_COUNT] as? Number)?.toInt() ?: 0,
            commentCount = (fields[COMMENT_COUNT] as? Number)?.toInt() ?: 0,
            isLiked = false,
            isBookmarked = false,
            timestamp = timestamp.toInstant()
        )
    }
}
