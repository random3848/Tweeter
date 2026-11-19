package edu.csuci.tweeter.notification

/**
 * The user a check notifies of popular posts.
 *
 * @property uid Their Firebase Authentication ID, so they aren't notified of their own posts.
 * @property notifiedPostIds The [edu.csuci.tweeter.data.Post.id]s of the posts they were already
 *     notified of as popular, so they aren't notified of one twice.
 */
data class PopularPostRecipient(val uid: String, val notifiedPostIds: Set<String>)
