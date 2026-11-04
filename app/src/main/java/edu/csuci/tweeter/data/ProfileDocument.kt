package edu.csuci.tweeter.data

/**
 * How a [UserProfile] is stored as a Firestore document in the [COLLECTION] collection.
 *
 * The document ID is [UserProfile.uid]. A user who hasn't set up a profile has no document.
 * A document is read field by field into an array in [Field] order, never as a whole map.
 */
object ProfileDocument {
    /** The collection every profile is stored in. */
    const val COLLECTION = "users"

    /** A stored field, whose ordinal is its index in the array [toProfile] reads. */
    enum class Field(
        /** The field's name in the document. */
        val key: String
    ) {
        /** The name the user shows. */
        DISPLAY_NAME("displayName"),

        /** The user's description of themselves. */
        BIO("bio"),

        /** Where the user's photo is. */
        PHOTO_URL("photoUrl")
    }

    /** Returns the fields to store for [profile]; its [UserProfile.uid] is the document ID. */
    fun fieldsOf(profile: UserProfile): Map<String, Any> = mapOf(
        Field.DISPLAY_NAME.key to profile.displayName,
        Field.BIO.key to profile.bio,
        Field.PHOTO_URL.key to profile.photoUrl
    )

    /**
     * Returns the profile of user [uid] stored with [fields], indexed by [Field.ordinal], which
     * are null when the user has no document yet.
     *
     * A missing, blank or wrongly typed field reads as its default from
     * [UserProfile.createDefault], so a document edited by hand in the Firebase console still
     * shows up.
     */
    fun toProfile(uid: String, fields: Array<Any?>?): UserProfile {
        val default = UserProfile.createDefault(uid)
        if (fields == null) {
            return default
        }

        return UserProfile(
            uid = uid,
            displayName = (fields[Field.DISPLAY_NAME.ordinal] as? String)
                ?.takeIf { it.isNotBlank() } ?:
                default.displayName,
            bio = fields[Field.BIO.ordinal] as? String ?: default.bio,
            photoUrl = fields[Field.PHOTO_URL.ordinal] as? String ?: default.photoUrl
        )
    }
}
