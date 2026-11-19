package edu.csuci.tweeter.notification

/**
 * Shows the user notifications about posts.
 *
 * The app uses [SystemPostNotifier]. Every kind of post notification goes through it, each in its
 * own [PostChannel].
 */
interface PostNotifier {
    /** Whether the user lets the app show notifications, which it asks them once for. */
    fun canNotify(): Boolean

    /** Shows [notification], unless the user doesn't let the app show notifications. */
    fun notify(notification: PostNotification)
}
