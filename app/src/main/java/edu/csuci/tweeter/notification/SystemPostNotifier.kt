package edu.csuci.tweeter.notification

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import edu.csuci.tweeter.R

/**
 * A [PostNotifier] that shows notifications in the system's notification shade, creating each
 * [PostChannel] before its first notification, if [createChannels] hasn't. Tapping one opens the
 * post in Feed, through [PostNotificationTap].
 *
 * A post's notification is tagged with its ID and numbered by its channel, so a post has at most
 * one notification of each channel.
 *
 * @param context Any context; only its application context is kept.
 */
class SystemPostNotifier(context: Context) : PostNotifier {
    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    override fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

    override fun canNotify(channel: PostChannel): Boolean =
        hasPermission() && manager.areNotificationsEnabled() && !isTurnedOff(channel)

    override fun notify(notification: PostNotification) {
        if (canNotify(notification.channel)) {
            manager.createNotificationChannel(systemChannelOf(notification.channel))
            show(notification.postId, notification.channel.ordinal, build(notification))
        }
    }

    /**
     * Creates every [PostChannel], so that the system's settings for the app list each one, where
     * users can turn it off, before its first notification. It calls the system, so it is for a
     * background thread.
     */
    fun createChannels() {
        manager.createNotificationChannelsCompat(PostChannel.entries.map(::systemChannelOf))
    }

    // A channel not created yet isn't turned off: it is created as it was declared.
    private fun isTurnedOff(channel: PostChannel): Boolean =
        manager.getNotificationChannelCompat(channel.id)?.importance ==
            NotificationManagerCompat.IMPORTANCE_NONE

    // Creating a channel that exists changes nothing but its name and description, so this needs
    // no record of which channels were created.
    private fun systemChannelOf(channel: PostChannel): NotificationChannelCompat =
        NotificationChannelCompat.Builder(channel.id, channel.importance)
            .setName(appContext.getString(channel.title))
            .setDescription(appContext.getString(channel.description))
            .build()

    @Suppress("MissingPermission") // Checked by canNotify(channel) in notify().
    private fun show(tag: String, id: Int, notification: Notification) {
        manager.notify(tag, id, notification)
    }

    private fun build(notification: PostNotification): Notification =
        NotificationCompat.Builder(appContext, notification.channel.id)
            .setSmallIcon(R.drawable.ic_location_pin)
            .setContentTitle(stringOf(notification.title))
            .setContentText(stringOf(notification.text))
            .setContentIntent(PostNotificationTap.pendingIntentFor(appContext, notification.postId))
            .setAutoCancel(true)
            .build()

    private fun stringOf(text: NotificationText): String =
        appContext.getString(text.resId, *text.args.toTypedArray())
}
