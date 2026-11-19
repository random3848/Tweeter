package edu.csuci.tweeter.notification

import edu.csuci.tweeter.location.GeoPoint

/**
 * The user a check notifies of posts.
 *
 * @property uid Their Firebase Authentication ID, so they aren't notified of their own posts.
 * @property location Where their device last was, which "near them" is measured from.
 */
data class Recipient(val uid: String, val location: GeoPoint)
