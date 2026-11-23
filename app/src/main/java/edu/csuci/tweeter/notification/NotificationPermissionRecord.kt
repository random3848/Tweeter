package edu.csuci.tweeter.notification

import edu.csuci.tweeter.data.NumberStore

// The key whose presence records that the app asked for permission to show notifications.
private const val REQUESTED_KEY = "notificationPermission.requested"

// What is saved under REQUESTED_KEY; only that something is saved there is read.
private const val REQUESTED = 1L

/**
 * Whether the app has asked the user for permission to show notifications, kept in [numbers], so
 * that it asks once and, if the user says no, never again.
 */
class NotificationPermissionRecord(private val numbers: NumberStore) {
    /** Whether the app has asked for the permission. */
    suspend fun wasRequested(): Boolean = numbers.getLong(REQUESTED_KEY) != null

    /** Records that the app asked for the permission. */
    suspend fun recordRequest() {
        numbers.putLongs(mapOf(REQUESTED_KEY to REQUESTED))
    }
}
