package edu.csuci.tweeter.notification

import edu.csuci.tweeter.data.NumberStore
import java.time.Clock

// The key of when the app asked for permission to show notifications.
private const val REQUESTED_AT_KEY = "notificationPermission.requestedAtMillis"

/**
 * Whether the app has asked the user for permission to show notifications, kept in [numbers], so
 * that it asks once and, if the user says no, never again. It saves when it asked, by [clock].
 */
class NotificationPermissionRecord(private val numbers: NumberStore, private val clock: Clock) {
    /** Whether the app has asked for the permission. */
    suspend fun wasRequested(): Boolean = numbers.getLong(REQUESTED_AT_KEY) != null

    /** Records that the app asked for the permission now. */
    suspend fun recordRequest() {
        numbers.putLongs(mapOf(REQUESTED_AT_KEY to clock.millis()))
    }
}
