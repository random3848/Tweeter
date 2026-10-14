package edu.csuci.tweeter.data

/**
 * A user's profile picture: their initials for now, and a photo once users can upload one.
 *
 * The pastel background behind the initials is not stored here; it comes from the theme, picked
 * by username, so the same user always gets the same color.
 *
 * @property initials One or two letters to show when there is no photo, such as "MK".
 * @property imageUrl The profile photo, or null to show [initials].
 */
data class Avatar(val initials: String, val imageUrl: String? = null)
