package edu.csuci.tweeter.data

/**
 * Numbers kept on the device by name, which outlive the app's process: what the app remembers
 * between runs, such as where the device last was, when it last checked for posts to notify the
 * user of, and which posts it notified them of.
 *
 * The app uses [SharedPreferencesNumberStore].
 */
interface NumberStore {
    /** The number saved under [key], or null if none is. */
    suspend fun getLong(key: String): Long?

    /** Every number saved under a key that starts with [prefix], by its key. */
    suspend fun getLongsWithPrefix(prefix: String): Map<String, Long>

    /** Saves each of [numbers] under its key, all in one write, replacing what was there. */
    suspend fun putLongs(numbers: Map<String, Long>)

    /** Removes the numbers saved under [keys], all in one write. Keys without one are skipped. */
    suspend fun removeLongs(keys: Collection<String>)
}
