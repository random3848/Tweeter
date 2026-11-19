package edu.csuci.tweeter.notification

import edu.csuci.tweeter.data.NumberStore

// Followed by a check's name, the key of the time it last checked up to.
private const val KEY_PREFIX = "lastCheckMillis."

/**
 * The time up to which the check named [checkName] last looked for posts to notify the user of, in
 * milliseconds since the epoch, kept in [numbers]. Each kind of check has its own name, and so its
 * own time; a check that keeps a time for each user has a name for each.
 *
 * Reading the time and moving it on aren't one atomic step, so only one check of a name may run at
 * a time, as WorkManager runs a unique periodic work.
 */
class LastCheckStore(private val numbers: NumberStore, checkName: String) {
    private val key = KEY_PREFIX + checkName

    /** The time up to which the check last looked, or null if it never did. */
    suspend fun lastCheckMillis(): Long? = numbers.getLong(key)

    /**
     * Saves [timeMillis] as the time up to which the check looked, unless a later one is saved:
     * the time never goes back, so the check never looks at posts it already looked at again.
     */
    suspend fun advanceTo(timeMillis: Long) {
        val lastCheckMillis = lastCheckMillis()
        if (lastCheckMillis == null || timeMillis > lastCheckMillis) {
            numbers.putLongs(mapOf(key to timeMillis))
        }
    }

    /**
     * Forgets the time, as if the check never looked, so that it starts over from the server's
     * newest post once it may notify the user again.
     */
    suspend fun clear() {
        numbers.removeLongs(listOf(key))
    }
}
