package edu.csuci.tweeter.data

/**
 * A user's profile picture, shown as their initials until users can upload a photo.
 *
 * @property initials One or two letters to show, such as "MK".
 */
data class Avatar(val initials: String)
