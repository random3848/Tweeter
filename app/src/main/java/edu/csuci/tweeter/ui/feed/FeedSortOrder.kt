package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.Post

/**
 * Options for sorting posts in the home feed.
 */
enum class FeedSortOrder {
    /** Orders posts with the most recent timestamp first. */
    MOST_RECENT_FIRST,

    /** Orders posts with the oldest timestamp first. */
    OLDEST_FIRST
}

/**
 * Sorts a list of posts according to the specified [order].
 *
 * @param order The sorting strategy to apply.
 * @return A new list of posts sorted by [order].
 */
fun List<Post>.sortPosts(order: FeedSortOrder): List<Post> = when (order) {
    FeedSortOrder.MOST_RECENT_FIRST -> sortedByDescending { it.timestamp }
    FeedSortOrder.OLDEST_FIRST -> sortedBy { it.timestamp }
}
