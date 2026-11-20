package edu.csuci.tweeter.ui.post

import edu.csuci.tweeter.location.LocationFix

/**
 * How far the post screen has got finding where the post's photo was taken. It belongs to the
 * photo: each new photo starts at [AwaitingPermission].
 */
sealed interface LocationStatus {
    /**
     * Waiting for the screen to ask for the permissions that finding the location takes. Whatever
     * the user answers, the screen then has it looked for.
     */
    data object AwaitingPermission : LocationStatus

    /** Looking for where the photo was taken. */
    data object Locating : LocationStatus

    /**
     * Known, so the post can be shared.
     *
     * @property fix Where the photo was taken: found, or picked by the user on the map.
     */
    data class Located(val fix: LocationFix) : LocationStatus

    /** Not found, or not looked for yet: the user has to pick it on the map. */
    data object NeedsPin : LocationStatus
}
