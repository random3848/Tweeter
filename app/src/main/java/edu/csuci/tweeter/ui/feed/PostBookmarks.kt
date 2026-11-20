package edu.csuci.tweeter.ui.feed

/**
 * The user's saved posts, as [PostList] shows them on a bookmark button under each post.
 *
 * @property bookmarkedPostIds The IDs of the posts the user saved, whose bookmarks are filled.
 * @property onBookmark Saves the post with the given ID or unsaves it, or opens Log in for guests.
 *     It takes the ID rather than capturing the table, so it stays the same as new tables arrive.
 */
internal data class PostBookmarks(
    val bookmarkedPostIds: Set<String>,
    val onBookmark: (String) -> Unit
)
