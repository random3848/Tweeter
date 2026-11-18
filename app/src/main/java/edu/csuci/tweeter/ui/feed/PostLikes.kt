package edu.csuci.tweeter.ui.feed

/**
 * The user's likes, as [PostList] shows them on a like button under each post.
 *
 * @property likedPostIds The IDs of the posts the user liked, whose hearts are filled.
 * @property onLike Likes the post with the given ID or takes the like back, or opens Log in for
 *     guests. It takes the ID rather than capturing the table, so it stays the same as new tables
 *     arrive.
 */
internal data class PostLikes(val likedPostIds: Set<String>, val onLike: (String) -> Unit)
