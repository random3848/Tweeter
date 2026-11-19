package edu.csuci.tweeter.notification

import androidx.annotation.StringRes

/**
 * A notification about one post, which opens the post in Feed when tapped.
 *
 * @property channel The kind of notification it is.
 * @property postId The [edu.csuci.tweeter.data.Post.id] of the post it is about. A post has at most
 *     one notification of each channel: a second one replaces the first.
 * @property title The notification's first line.
 * @property text The line under [title].
 */
data class PostNotification(
    val channel: PostChannel,
    val postId: String,
    val title: NotificationText,
    val text: NotificationText
)

/**
 * A line of a notification, which the [PostNotifier] looks up in the device's language.
 *
 * @property resId The line's string resource.
 * @property args What fills the resource's placeholders, in order.
 */
data class NotificationText(@StringRes val resId: Int, val args: List<String>) {
    /** A line of [resId] formatted with [args]. */
    constructor(@StringRes resId: Int, vararg args: String) : this(resId, args.toList())
}
