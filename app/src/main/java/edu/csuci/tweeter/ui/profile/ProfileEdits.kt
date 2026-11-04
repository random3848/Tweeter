package edu.csuci.tweeter.ui.profile

import edu.csuci.tweeter.data.UserProfile

/**
 * The name and bio a user is typing on the Edit profile screen, before they're saved.
 *
 * Both are kept as typed; [applyTo] trims them.
 *
 * @property displayName The name field's text.
 * @property bio The bio field's text.
 */
data class ProfileEdits(val displayName: String, val bio: String) {
    /**
     * Whether these edits can be saved: once trimmed, the name isn't blank and neither the name
     * nor the bio is longer than [UserProfile] allows.
     */
    val isValid: Boolean
        get() {
            val name = displayName.trim()
            return name.isNotEmpty() &&
                name.length <= UserProfile.MAX_DISPLAY_NAME_LENGTH &&
                bio.trim().length <= UserProfile.MAX_BIO_LENGTH
        }

    /**
     * Returns [profile] with the trimmed name and bio.
     *
     * @throws IllegalArgumentException If the edits aren't [isValid].
     */
    fun applyTo(profile: UserProfile): UserProfile {
        require(isValid) { "Can't apply an invalid name or bio to a profile" }
        return profile.copy(displayName = displayName.trim(), bio = bio.trim())
    }

    companion object {
        /** Returns edits that start from [profile]'s current name and bio. */
        fun of(profile: UserProfile): ProfileEdits =
            ProfileEdits(displayName = profile.displayName, bio = profile.bio)
    }
}
