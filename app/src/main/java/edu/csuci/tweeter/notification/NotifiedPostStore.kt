package edu.csuci.tweeter.notification

import edu.csuci.tweeter.data.NumberStore
import edu.csuci.tweeter.data.PostTable

// Followed by a check's name, a user's ID and a post's ID, each after a dot, the key of when that
// post was shared.
private const val KEY_PREFIX = "notifiedPost."

/**
 * The posts the check named [checkName] notified the user [uid] of, kept in [numbers] with when
 * each was shared, so that the check notifies of each once, also after the app restarts. Each kind
 * of check has its own name, and each user of the device their own posts, so that what one account
 * was notified of doesn't keep another from hearing of it.
 *
 * It stays small: the check forgets each post with [forgetPostsSharedUpTo] once the post is too
 * old for the check to look at again, so it holds at most the posts the check notified of within
 * that time.
 *
 * Reading the posts and changing them aren't one atomic step, so only one check of a name may run
 * at a time, as WorkManager runs a unique periodic work.
 */
class NotifiedPostStore(private val numbers: NumberStore, checkName: String, uid: String) {
    private val keyPrefix = "$KEY_PREFIX$checkName.$uid."

    /** The [edu.csuci.tweeter.data.Post.id]s of the posts the check notified of. */
    suspend fun notifiedPostIds(): Set<String> =
        numbers.getLongsWithPrefix(keyPrefix).keys.mapTo(HashSet()) { key ->
            key.removePrefix(keyPrefix)
        }

    /** Remembers that the check notified of the posts in [rows] of [posts], all in one write. */
    suspend fun remember(posts: PostTable, rows: IntArray) {
        val sharedMillisByKey = rows.associate { row ->
            keyPrefix + posts.id(row) to posts.timestampMillis(row)
        }
        numbers.putLongs(sharedMillisByKey)
    }

    /**
     * Forgets the posts shared at [timeMillis] (in milliseconds since the epoch, as
     * [PostTable.timestampMillis] gives it) or before, which the check can no longer notify of.
     */
    suspend fun forgetPostsSharedUpTo(timeMillis: Long) {
        val sharedMillisByKey = numbers.getLongsWithPrefix(keyPrefix)
        val expiredKeys = sharedMillisByKey.filterValues { it <= timeMillis }.keys
        numbers.removeLongs(expiredKeys)
    }
}
