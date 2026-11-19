package edu.csuci.tweeter.notification

import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.PostLookup
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.location.LastLocationStore
import java.time.Clock

// The row of a PostTable holding its newest post.
private const val NEWEST_ROW = 0

/**
 * One check for rare animals posted near the logged-in user since the last check, which notifies
 * them of each, once. [RareAnimalCheckWorker] runs it every so often.
 *
 * The user hears of the posts by others that [selector] chooses, measured from where
 * [lastLocationStore] says the device last was. A check notifies of nothing, and only moves
 * [lastCheckStore] on to now, by [clock], when it is the first, or when nobody is logged in, the
 * app may not show notifications, or the device's location is unknown. A later check then doesn't
 * notify of posts that are old by then.
 *
 * @param accountRepository Who is logged in, whom the check notifies.
 * @param notifier Shows the notifications, if the user lets it.
 * @param lastLocationStore Where the device last was.
 * @param lastCheckStore Up to when the last check looked; the check after it looks at posts the
 *     server received since. It never goes back, so no post is notified of twice.
 * @param postLookup Where new posts are looked up.
 * @param selector Chooses which new posts the user hears of.
 * @param clock The time now, which a check that looks at no posts moves [lastCheckStore] on to.
 */
class RareAnimalCheck(
    private val accountRepository: AccountRepository,
    private val notifier: PostNotifier,
    private val lastLocationStore: LastLocationStore,
    private val lastCheckStore: LastCheckStore,
    private val postLookup: PostLookup,
    private val selector: NearbyRareAnimalSelector,
    private val clock: Clock
) {
    /**
     * Notifies the user of each rare animal posted near them since the last check.
     *
     * @throws edu.csuci.tweeter.data.PostLookupException If the posts can't be looked up; the next
     *     check then looks at them again.
     */
    suspend fun run() {
        val lastCheckMillis = lastCheckStore.lastCheckMillis()
        val recipient = recipient()
        if (lastCheckMillis == null || recipient == null) {
            lastCheckStore.advanceTo(clock.millis())
        } else {
            notifyOfPostsAfter(lastCheckMillis, recipient)
        }
    }

    // The logged-in user, if the app may notify them and knows where they are.
    private suspend fun recipient(): Recipient? {
        val uid = accountRepository.loggedInUid.value ?: return null
        if (!notifier.canNotify()) {
            return null
        }
        val location = lastLocationStore.lastLocation() ?: return null
        return Recipient(uid, location)
    }

    // The new posts' rows are chosen before the last check moves on, so a failed lookup leaves
    // them for the next check, and the notifications are shown after, so a check that stops
    // midway never shows one twice.
    private suspend fun notifyOfPostsAfter(lastCheckMillis: Long, recipient: Recipient) {
        val newPosts = postLookup.postsReceivedAfter(lastCheckMillis)
        if (newPosts.size == 0) {
            return
        }
        val rows = selector.rowsToNotify(newPosts, recipient)
        lastCheckStore.advanceTo(newPosts.timestampMillis(NEWEST_ROW))
        for (row in rows) {
            notifier.notify(rareAnimalNotification(newPosts, row))
        }
    }

    private fun rareAnimalNotification(posts: PostTable, row: Int): PostNotification {
        val animal = posts.animalType(row).trim()
        val place = posts.locationName(row).trim()
        val text = if (place.isEmpty()) {
            NotificationText(R.string.notification_rare_animal_text_no_place)
        } else {
            NotificationText(R.string.notification_rare_animal_text, place)
        }
        return PostNotification(
            channel = PostChannel.ANIMALS_NEAR_YOU,
            postId = posts.id(row),
            title = NotificationText(R.string.notification_rare_animal_title, animal),
            text = text
        )
    }
}
