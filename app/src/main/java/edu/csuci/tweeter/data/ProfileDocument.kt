package edu.csuci.tweeter.data

/**
 * How a [UserProfile] is stored as a Firestore document in the [COLLECTION] collection.
 *
 * The document ID is [UserProfile.uid]. A user who hasn't set up a profile has no document.
 */
object ProfileDocument {
    /** The collection every profile is stored in. */
    const val COLLECTION = "users"

    private const val DISPLAY_NAME = "displayName"
    private const val BIO = "bio"
    private const val PHOTO_URL = "photoUrl"

    /**
     * Returns the profile of user [uid] stored with [fields], which are null when the user has no
     * document yet.
     *
     * A missing, blank or wrongly typed field reads as its default from
     * [UserProfile.createDefault], so a document edited by hand in the Firebase console still
     * shows up.
     */
    fun toProfile(uid: String, fields: Map<String, Any?>?): UserProfile {
        val default = UserProfile.createDefault(uid)
        if (fields == null) {
            return default
        }

        return UserProfile(
            uid = uid,
            displayName = (fields[DISPLAY_NAME] as? String)
                ?.takeIf { it.isNotBlank() } ?:
                default.displayName,
            bio = fields[BIO] as? String ?: default.bio,
            photoUrl = fields[PHOTO_URL] as? String ?: default.photoUrl
        )
    }
}
