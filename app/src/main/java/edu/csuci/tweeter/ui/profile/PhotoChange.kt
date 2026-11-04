package edu.csuci.tweeter.ui.profile

/** What saving on the Edit profile screen does with the profile photo. */
sealed interface PhotoChange {
    /** Keeps the photo the profile has, or keeps it without one. */
    data object Unchanged : PhotoChange

    /**
     * Replaces the photo with a new one, uploaded when the profile is saved.
     *
     * @property photoUri Where the new photo is on the phone: a `content://` or `file://` URI.
     */
    data class Replace(val photoUri: String) : PhotoChange

    /** Removes the photo, so the profile shows its initials instead. */
    data object Remove : PhotoChange
}
