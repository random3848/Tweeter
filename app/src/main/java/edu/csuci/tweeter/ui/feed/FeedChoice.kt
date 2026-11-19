package edu.csuci.tweeter.ui.feed

import androidx.annotation.StringRes
import edu.csuci.tweeter.R

/**
 * What the feed shows, as the user picks with the chips above the posts: everyone's posts in one
 * of three orders, or only their friends' posts.
 *
 * @property labelRes The choice's name on its chip.
 * @property order The order the posts are shown in.
 * @property showsOnlyFriends Whether only the user's friends' posts are shown: the users who follow
 *     them and whom they follow back. Guests have no friends, so it's offered only to users who are
 *     logged in.
 */
enum class FeedChoice(
    @StringRes val labelRes: Int,
    val order: FeedOrder,
    val showsOnlyFriends: Boolean = false
) {
    /** Everyone's posts, recent posts first, lifted by their likes. */
    BEST(R.string.feed_order_best, FeedOrder.BEST),

    /** Everyone's posts, newest first. */
    NEWEST(R.string.feed_order_newest, FeedOrder.NEWEST),

    /** Everyone's posts, most liked first. */
    TOP(R.string.feed_order_top, FeedOrder.TOP),

    /** The user's friends' posts, newest first. */
    FRIENDS(R.string.feed_friends, FeedOrder.NEWEST, showsOnlyFriends = true);

    /**
     * The choice that shows everyone's posts in this one's order: this one, unless it shows only
     * friends.
     */
    fun forEveryone(): FeedChoice =
        entries.first { choice -> !choice.showsOnlyFriends && choice.order == order }

    companion object {
        /** The choices offered on the chips, in their order: all of them unless it's a guest. */
        fun offeredTo(isLoggedIn: Boolean): List<FeedChoice> =
            entries.filter { choice -> isLoggedIn || !choice.showsOnlyFriends }
    }
}
