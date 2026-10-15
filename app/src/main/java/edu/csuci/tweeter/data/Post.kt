package edu.csuci.tweeter.data

import java.time.Instant

/**
 * A photo of something in nature that a user shared, with where and when it was taken.
 *
 * Feed shows posts as a list and Map shows them as pins, so this is the one shape every screen
 * agrees on. Screens get posts from a [PostRepository].
 *
 * @property id Unique among all posts.
 * @property username The poster's handle, such as "maya.k".
 * @property avatar The poster's profile picture.
 * @property locationName A human-readable place, such as "Library, oak tree". It is a label for
 *     people; [latitude] and [longitude] are where the pin goes.
 * @property latitude Where the photo was taken, in degrees.
 * @property longitude Where the photo was taken, in degrees.
 * @property imageUri The photo, as a URI string (`content://` from the photo picker, or
 *     `https://` once photos are stored online). Empty when there is no photo yet.
 * @property caption What the poster wrote about the photo; may be empty.
 * @property likeCount How many users liked the post.
 * @property commentCount How many comments the post has.
 * @property isLiked Whether the signed-in user liked the post.
 * @property isBookmarked Whether the signed-in user saved the post.
 * @property timestamp When the post was shared. Screens show it as relative time ("12 minutes
 *     ago").
 */
data class Post(
    val id: String,
    val username: String,
    val avatar: Avatar,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val imageUri: String,
    val caption: String,
    val likeCount: Int,
    val commentCount: Int,
    val isLiked: Boolean,
    val isBookmarked: Boolean,
    val timestamp: Instant
)
