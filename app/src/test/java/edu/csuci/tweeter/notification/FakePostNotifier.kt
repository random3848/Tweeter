package edu.csuci.tweeter.notification

/**
 * A [PostNotifier] for tests, which keeps the notifications it posts while [isAllowed] and the
 * notification's channel isn't in [turnedOffChannels].
 */
class FakePostNotifier(var isAllowed: Boolean = true) : PostNotifier {
    /** The notifications posted, in order. */
    val notifications = mutableListOf<PostNotification>()

    /** The channels the user turned off. */
    val turnedOffChannels = mutableSetOf<PostChannel>()

    override fun hasPermission(): Boolean = isAllowed

    override fun canNotify(channel: PostChannel): Boolean =
        isAllowed && channel !in turnedOffChannels

    override fun notify(notification: PostNotification) {
        if (canNotify(notification.channel)) {
            notifications += notification
        }
    }
}
