package edu.csuci.tweeter.notification

import edu.csuci.tweeter.R
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.PostLookup
import edu.csuci.tweeter.data.PostTable

/**
 * One check for posts by others that have become popular, which notifies the logged-in user of
 * each, once. [PopularPostCheckWorker] runs it every so often.
 *
 * Unlike the posts [RareAnimalCheck] looks for, popular posts aren't new: a post becomes popular
 * some time after it is shared. So each check looks at every post that is still recent, as
 * [selector] judges, and remembers the posts it notified the user of, in their store from
 * [notifiedPostStores], until they are too old to look at again. A check while nobody is logged
 * in, or the user turned off the channel or notifications, looks nothing up, and a post that
 * becomes popular meanwhile is notified of once the user can be, if it is still recent then.
 *
 * Times are the server's, never the device's, whose clock may be wrong: a post's age is measured
 * to when the server received its newest post. So while nobody posts, the posts before stay
 * recent.
 *
 * @param accountRepository Who is logged in, whom the check notifies.
 * @param notifier Shows the notifications, if the user lets it.
 * @param notifiedPostStores Gets the posts a user was notified of as popular, by their ID.
 * @param postLookup Where recent posts are looked up, and the server's time.
 * @param selector Chooses which recent posts the user hears of, and says how recent they must be.
 */
class PopularPostCheck(
    private val accountRepository: AccountRepository,
    private val notifier: PostNotifier,
    private val notifiedPostStores: (uid: String) -> NotifiedPostStore,
    private val postLookup: PostLookup,
    private val selector: PopularPostSelector
) : PostCheck {
    /**
     * Notifies the user of each recent post by someone else that is popular now, unless they were
     * notified of it before.
     *
     * @throws edu.csuci.tweeter.data.PostLookupException If the posts can't be looked up; the next
     *     check then looks at them again.
     */
    override suspend fun run() {
        val uid = accountRepository.loggedInUid.value
        if (uid != null && notifier.canNotify(PostChannel.POPULAR_POSTS)) {
            notifyOfPopularPosts(uid)
        }
    }

    // The posts are remembered before the notifications are shown, so a check that stops midway
    // never shows one twice. With no posts on the server, there is nothing to notify of.
    private suspend fun notifyOfPopularPosts(uid: String) {
        val nowMillis = postLookup.newestPostMillis() ?: return
        val recentAfterMillis = selector.recentAfterMillis(nowMillis)
        val recentPosts = postLookup.postsReceivedAfter(recentAfterMillis)
        val notifiedPosts = notifiedPostStores(uid)
        notifiedPosts.forgetPostsSharedUpTo(recentAfterMillis)
        val recipient = PopularPostRecipient(uid, notifiedPosts.notifiedPostIds())
        val rows = selector.rowsToNotify(recentPosts, nowMillis, recipient)
        notifiedPosts.remember(recentPosts, rows)
        for (row in rows) {
            notifier.notify(popularPostNotification(recentPosts, row))
        }
    }

    private fun popularPostNotification(posts: PostTable, row: Int): PostNotification =
        PostNotification(
            channel = PostChannel.POPULAR_POSTS,
            postId = posts.id(row),
            title = titleOf(posts, row),
            text = textOf(posts, row)
        )

    // Names the post's animal, if it names one.
    private fun titleOf(posts: PostTable, row: Int): NotificationText {
        val animal = posts.animalType(row).trim()
        return if (animal.isEmpty()) {
            NotificationText(R.string.notification_popular_post_title)
        } else {
            NotificationText(R.string.notification_popular_post_title_animal, animal)
        }
    }

    // Gives the post's likes, and its place, if it has one.
    private fun textOf(posts: PostTable, row: Int): NotificationText {
        val likeCount = posts.likeCount(row).toString()
        val place = posts.locationName(row).trim()
        return if (place.isEmpty()) {
            NotificationText(R.string.notification_popular_post_text_no_place, likeCount)
        } else {
            NotificationText(R.string.notification_popular_post_text, likeCount, place)
        }
    }
}
