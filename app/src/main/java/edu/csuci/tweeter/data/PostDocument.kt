package edu.csuci.tweeter.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

/**
 * How a [Post] is stored as a Firestore document in the [COLLECTION] collection.
 *
 * The document ID is [Post.id]. [Post.isLiked] and [Post.isBookmarked] depend on who is looking,
 * so they aren't stored and read back as false. [Post.timestamp] isn't stored either:
 * Firestore sets [Field.TIMESTAMP] to when it receives the post, so a phone with a wrong clock
 * can't misdate it.
 *
 * A document is read field by field into an array in [Field] order, never as a whole map.
 */
object PostDocument {
    /** The collection every post is stored in. */
    const val COLLECTION = "posts"

    private const val MIN_CONFIDENCE = 1
    private const val MAX_CONFIDENCE = 5

    /** A stored field, whose ordinal is its index in the array [toPost] reads. */
    enum class Field(override val key: String) : DocumentField {
        /** The poster's ID. */
        AUTHOR_UID("authorUid"),

        /** The poster's name. */
        USERNAME("username"),

        /** The initials on the poster's avatar. */
        AVATAR_INITIALS("avatarInitials"),

        /** The place the photo was taken. */
        LOCATION_NAME("locationName"),

        /** The photo's latitude. */
        LATITUDE("latitude"),

        /** The photo's longitude. */
        LONGITUDE("longitude"),

        /** Where the photo is. */
        IMAGE_URI("imageUri"),

        /** The poster's text. */
        CAPTION("caption"),

        /** How many likes the post has. */
        LIKE_COUNT("likeCount"),

        /** How many comments the post has. */
        COMMENT_COUNT("commentCount"),

        /** When Firestore received the post. */
        TIMESTAMP("timestamp"),

        /** The animal in the photo. */
        ANIMAL_TYPE("animalType"),

        /** How sure the poster is of [ANIMAL_TYPE], 1 to 5. */
        CONFIDENCE("confidence")
    }

    /**
     * Returns the fields to store for [post], with [Field.TIMESTAMP] left for Firestore to set and
     * [Field.AUTHOR_UID] left out when it is empty.
     */
    fun fieldsOf(post: Post): Map<String, Any> = buildMap {
        put(Field.USERNAME.key, post.username)
        put(Field.AVATAR_INITIALS.key, post.avatar.initials)
        put(Field.LOCATION_NAME.key, post.locationName)
        put(Field.LATITUDE.key, post.latitude)
        put(Field.LONGITUDE.key, post.longitude)
        put(Field.IMAGE_URI.key, post.imageUri)
        put(Field.CAPTION.key, post.caption)
        put(Field.ANIMAL_TYPE.key, post.animalType)
        put(Field.LIKE_COUNT.key, post.likeCount)
        put(Field.COMMENT_COUNT.key, post.commentCount)
        put(Field.TIMESTAMP.key, FieldValue.serverTimestamp())
        if (post.authorUid.isNotEmpty()) put(Field.AUTHOR_UID.key, post.authorUid)
        post.confidence?.let { put(Field.CONFIDENCE.key, it) }
    }

    /**
     * Returns the post stored in document [id] with [fields], indexed by [Field.ordinal], or null
     * if a required field is missing or has the wrong type.
     *
     * The text fields that may be empty ([Field.AUTHOR_UID] among them) and the counts default
     * when missing, so a post added by hand in the Firebase console only needs [Field.USERNAME],
     * [Field.LATITUDE], [Field.LONGITUDE], [Field.IMAGE_URI] and [Field.TIMESTAMP]. A confidence
     * outside 1 to 5 is dropped.
     */
    fun toPost(id: String, fields: Array<Any?>): Post? {
        val username = fields[Field.USERNAME.ordinal] as? String ?: return null
        // Firestore returns a whole number typed in the console as a Long.
        val latitude = (fields[Field.LATITUDE.ordinal] as? Number)?.toDouble() ?: return null
        val longitude = (fields[Field.LONGITUDE.ordinal] as? Number)?.toDouble() ?: return null
        val imageUri = fields[Field.IMAGE_URI.ordinal] as? String ?: return null
        val timestamp = fields[Field.TIMESTAMP.ordinal] as? Timestamp ?: return null

        return Post(
            id = id,
            authorUid = textIn(fields, Field.AUTHOR_UID),
            username = username,
            avatar = Avatar(
                initials = fields[Field.AVATAR_INITIALS.ordinal] as? String ?:
                    username.take(1).uppercase()
            ),
            locationName = textIn(fields, Field.LOCATION_NAME),
            latitude = latitude,
            longitude = longitude,
            imageUri = imageUri,
            caption = textIn(fields, Field.CAPTION),
            likeCount = countIn(fields, Field.LIKE_COUNT),
            commentCount = countIn(fields, Field.COMMENT_COUNT),
            isLiked = false,
            isBookmarked = false,
            timestamp = timestamp.toInstant(),
            animalType = textIn(fields, Field.ANIMAL_TYPE),
            confidence = (fields[Field.CONFIDENCE.ordinal] as? Number)?.toInt()
                ?.takeIf { it in MIN_CONFIDENCE..MAX_CONFIDENCE }
        )
    }

    private fun textIn(fields: Array<Any?>, field: Field): String =
        fields[field.ordinal] as? String ?: ""

    private fun countIn(fields: Array<Any?>, field: Field): Int =
        (fields[field.ordinal] as? Number)?.toInt() ?: 0
}
