package edu.csuci.tweeter.notification

import edu.csuci.tweeter.location.GeoPoint

/**
 * The user a check notifies of rare animals near them.
 *
 * @property uid Their Firebase Authentication ID, so they aren't notified of their own posts.
 * @property location Where their device last was, which "near them" is measured from.
 * @property notifiedPostIds The [edu.csuci.tweeter.data.Post.id]s of the posts they were already
 *     notified of, or counted as notified of, so they aren't notified of one twice.
 */
data class Recipient(val uid: String, val location: GeoPoint, val notifiedPostIds: Set<String>)
