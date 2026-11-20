package edu.csuci.tweeter.ui.post

/** How far sharing the post has got. */
sealed interface ShareStatus {
    /** Not being shared, and nothing to report about the last attempt. */
    data object Idle : ShareStatus

    /** Uploading the photo and adding the post. */
    data object Uploading : ShareStatus

    /**
     * Sharing failed the last time it was tried.
     *
     * @property photoUri The photo that couldn't be shared.
     */
    data class Failed(val photoUri: String) : ShareStatus

    /**
     * Shared as a post, and the photo forgotten.
     *
     * @property photoUrl The URL the post's photo was uploaded to.
     */
    data class Shared(val photoUrl: String) : ShareStatus
}
