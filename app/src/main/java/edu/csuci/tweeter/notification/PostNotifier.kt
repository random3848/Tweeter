package edu.csuci.tweeter.notification

/**
 * Shows the user notifications about posts.
 *
 * The app uses [SystemPostNotifier]. Every kind of post notification goes through it, each in its
 * own [PostChannel].
 */
interface PostNotifier {
    /** Whether the app has permission to show notifications, which it asks the user once for. */
    fun hasPermission(): Boolean

    /**
     * Whether the user lets the app show notifications of [channel]: they gave it permission, and
     * turned off neither the app's notifications nor that channel in the system's settings.
     */
    fun canNotify(channel: PostChannel): Boolean

    /** Shows [notification], unless the user doesn't let the app show its channel. */
    fun notify(notification: PostNotification)
}
