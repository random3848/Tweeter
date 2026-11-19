package edu.csuci.tweeter.notification

/** A [PostNotifier] for tests, which keeps the notifications it posts while [canNotify] allows. */
class FakePostNotifier(var isAllowed: Boolean = true) : PostNotifier {
    /** The notifications posted, in order. */
    val notifications = mutableListOf<PostNotification>()

    override fun canNotify(): Boolean = isAllowed

    override fun notify(notification: PostNotification) {
        if (isAllowed) {
            notifications += notification
        }
    }
}
