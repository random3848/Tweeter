package edu.csuci.tweeter.notification

import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import edu.csuci.tweeter.R

/**
 * A kind of notification about posts, each its own Android notification channel, which users can
 * turn off or change in the system's settings for the app.
 *
 * @property id The channel's ID, which Android keeps with the user's settings for it, so it never
 *     changes.
 * @property title The channel's name in the system's settings.
 * @property description What the channel notifies of, under its name in the system's settings.
 * @property importance How much its notifications interrupt, as a
 *     `NotificationManagerCompat.IMPORTANCE_*` value.
 */
enum class PostChannel(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val importance: Int
) {
    /** Posts of rare animals near where the user last was. */
    ANIMALS_NEAR_YOU(
        id = "animals_near_you",
        title = R.string.notification_channel_animals_near_you,
        description = R.string.notification_channel_animals_near_you_description,
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT
    ),

    /** Recent posts by others that many users liked, wherever they were shared. */
    POPULAR_POSTS(
        id = "popular_posts",
        title = R.string.notification_channel_popular_posts,
        description = R.string.notification_channel_popular_posts_description,
        importance = NotificationManagerCompat.IMPORTANCE_DEFAULT
    )
}
