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
 * some time after it is shared. So each check reads every post with the likes [selector] calls
 * popular, however old, since the server can't also filter them by time without a composite
 * index, keeps those still recent, and remembers the posts it notified the user of, in their store
 * from [notifiedPostStores], until they are too old to look at again.
 *
 * The first check for a user notifies them of nothing: it remembers the posts popular then as
 * notified of, so that the user hears of the posts that become popular after, not of every recent
 * one at once. So does a check after the user turned the channel or notifications off, which makes
 * a check forget when it last ran for them and look nothing up, and a check for a user it last ran
 * for before the posts recent now were shared. A check while nobody is logged in looks nothing up,
 * and a user who logs back in sooner hears of the posts that became popular meanwhile.
 *
 * Times are the server's, never the device's, whose clock may be wrong: a post's age is measured
 * to when the server received its newest post. So while nobody posts, the posts before stay
 * recent.
 *
 * @param accountRepository Who is logged in, whom the check notifies.
 * @param notifier Shows the notifications, if the user lets it.
 * @param lastCheckStores Gets when, by the server's clock, the check last ran for a user, by their
 *     ID.
 * @param notifiedPostStores Gets the posts a user was notified of as popular, by their ID; the
 *     check also counts the posts popular at its first run for them as notified of.
 * @param postLookup Where popular posts are looked up, and the server's time.
 * @param selector Chooses which popular posts the user hears of, and says how many likes make a
 *     post popular and how recent it must be.
 */
class PopularPostCheck(
    private val accountRepository: AccountRepository,
    private val notifier: PostNotifier,
    private val lastCheckStores: (uid: String) -> LastCheckStore,
    private val notifiedPostStores: (uid: String) -> NotifiedPostStore,
    private val postLookup: PostLookup,
    private val selector: PopularPostSelector
) : PostCheck {
    /**
     * Notifies the user of each recent post by someone else that is popular now, unless they were
     * notified of it before or it was popular already when the check started for them.
     *
     * @throws edu.csuci.tweeter.data.PostLookupException If the posts can't be looked up; the next
     *     check then looks at them again.
     */
    override suspend fun run() {
        val uid = accountRepository.loggedInUid.value ?: return
        if (notifier.canNotify(PostChannel.POPULAR_POSTS)) {
            checkPopularPosts(uid)
        } else {
            lastCheckStores(uid).clear()
        }
    }

    // The posts are remembered, and the time saved, before the notifications are shown, so a
    // check that stops midway never shows one twice. With no posts on the server, there is
    // nothing to notify of, and no time to start from.
    private suspend fun checkPopularPosts(uid: String) {
        val nowMillis = postLookup.newestPostMillis() ?: return
        val recentAfterMillis = selector.recentAfterMillis(nowMillis)
        val popularPosts = postLookup.postsWithLikesAtLeast(selector.minLikeCount)
        val notifiedPosts = notifiedPostStores(uid)
        notifiedPosts.forgetPostsSharedUpTo(recentAfterMillis)
        val recipient = PopularPostRecipient(uid, notifiedPosts.notifiedPostIds())
        val rows = selector.rowsToNotify(popularPosts, nowMillis, recipient)
        val lastCheckStore = lastCheckStores(uid)
        val ranSinceRecentPosts = ranSince(lastCheckStore.lastCheckMillis(), recentAfterMillis)
        notifiedPosts.remember(popularPosts, rows)
        lastCheckStore.advanceTo(nowMillis)
        if (ranSinceRecentPosts) {
            notifyOf(popularPosts, rows)
        }
    }

    // Whether the check last ran for the user after [timeMillis]. If it never did, or before, it
    // didn't see the posts popular now become popular, and notifying of them would bring them all
    // at once.
    private fun ranSince(lastCheckMillis: Long?, timeMillis: Long): Boolean =
        lastCheckMillis != null && lastCheckMillis > timeMillis

    private fun notifyOf(posts: PostTable, rows: IntArray) {
        for (row in rows) {
            notifier.notify(popularPostNotification(posts, row))
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
