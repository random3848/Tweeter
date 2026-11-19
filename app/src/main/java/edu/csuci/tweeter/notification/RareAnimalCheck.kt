package edu.csuci.tweeter.notification

import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.PostLookup
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.location.LastLocationStore

// The row of a PostTable holding its newest post.
private const val NEWEST_ROW = 0

// How far behind the last check a check looks again. A post's time is when the server received
// it, so a post received just before another can be saved just after it, and be missing from a
// check that already saw the other.
private const val SETTLE_WINDOW_MILLIS = 60_000L

// Where a check starts when the server holds no posts yet: every post it then gets is new.
private const val EPOCH_MILLIS = 0L

/**
 * One check for rare animals posted near the logged-in user since the last check, which notifies
 * them of each, once. [RareAnimalCheckWorker] runs it every so often.
 *
 * The user hears of the posts by others that [selector] chooses, measured from where
 * [lastLocationStore] says the device last was. While nobody can be notified (nobody is logged
 * in, the user turned off the channel or notifications, or the device's location is unknown), a
 * check forgets [lastCheckStore]. The first check that can notify then starts from the newest post
 * on the server, by the server's clock rather than the device's, and notifies of nothing older.
 *
 * Each check looks again at the posts received within [SETTLE_WINDOW_MILLIS] before the last
 * check, so it also finds a post saved after a later one. [selector] skips the posts in
 * [notifiedPostStores] before it judges any animal's rarity, which takes a count on the server.
 *
 * @param accountRepository Who is logged in, whom the check notifies.
 * @param notifier Shows the notifications, if the user lets it.
 * @param lastLocationStore Where the device last was.
 * @param lastCheckStore Up to when, by the server's clock, the last check looked.
 * @param notifiedPostStores Gets the posts a user was notified of, by their ID; the check also
 *     counts the posts already on the server when it starts as notified of.
 * @param postLookup Where new posts are looked up.
 * @param selector Chooses which new posts the user hears of.
 */
class RareAnimalCheck(
    private val accountRepository: AccountRepository,
    private val notifier: PostNotifier,
    private val lastLocationStore: LastLocationStore,
    private val lastCheckStore: LastCheckStore,
    private val notifiedPostStores: (uid: String) -> NotifiedPostStore,
    private val postLookup: PostLookup,
    private val selector: NearbyRareAnimalSelector
) : PostCheck {
    /**
     * Notifies the user of each rare animal posted near them since the last check.
     *
     * @throws edu.csuci.tweeter.data.PostLookupException If the posts can't be looked up; the next
     *     check then looks at them again.
     */
    override suspend fun run() {
        val recipient = recipient()
        val lastCheckMillis = lastCheckStore.lastCheckMillis()
        when {
            recipient == null -> lastCheckStore.clear()
            lastCheckMillis == null -> startFromNewestPost(recipient)
            else -> notifyOfPostsAfter(lastCheckMillis, recipient)
        }
    }

    // The logged-in user, if the app may notify them of rare animals and knows where they are.
    private suspend fun recipient(): Recipient? {
        val uid = accountRepository.loggedInUid.value ?: return null
        if (!notifier.canNotify(PostChannel.ANIMALS_NEAR_YOU)) {
            return null
        }
        val location = lastLocationStore.lastLocation() ?: return null
        return Recipient(uid, location, notifiedPostStores(uid).notifiedPostIds())
    }

    // The posts within the settle window of the newest one are already on the server, so the next
    // check, which looks at them again, counts them as notified of.
    private suspend fun startFromNewestPost(recipient: Recipient) {
        val newestMillis = postLookup.newestPostMillis() ?: EPOCH_MILLIS
        val settlingPosts = postLookup.postsReceivedAfter(newestMillis - SETTLE_WINDOW_MILLIS)
        val oldRows = (0 until settlingPosts.size).filter { row ->
            settlingPosts.timestampMillis(row) <= newestMillis
        }
        notifiedPostStores(recipient.uid).remember(settlingPosts, oldRows.toIntArray())
        lastCheckStore.advanceTo(newestMillis)
    }

    // The new posts' rows are chosen before anything is saved, so a failed lookup leaves them for
    // the next check, and the notifications are shown after, so a check that stops midway never
    // shows one twice.
    private suspend fun notifyOfPostsAfter(lastCheckMillis: Long, recipient: Recipient) {
        val posts = postLookup.postsReceivedAfter(lastCheckMillis - SETTLE_WINDOW_MILLIS)
        if (posts.size == 0) {
            return
        }
        val notifiedPosts = notifiedPostStores(recipient.uid)
        val rows = selector.rowsToNotify(posts, recipient)
        notifiedPosts.remember(posts, rows)
        val checkedUpToMillis = maxOf(lastCheckMillis, posts.timestampMillis(NEWEST_ROW))
        lastCheckStore.advanceTo(checkedUpToMillis)
        notifiedPosts.forgetPostsSharedUpTo(checkedUpToMillis - SETTLE_WINDOW_MILLIS)
        for (row in rows) {
            notifier.notify(rareAnimalNotification(posts, row))
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
