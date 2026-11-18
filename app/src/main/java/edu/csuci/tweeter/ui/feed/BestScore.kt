package edu.csuci.tweeter.ui.feed

import java.util.concurrent.TimeUnit.HOURS
import kotlin.math.pow

// How fast a post sinks as it ages: the age term is raised to this power, as on Hacker News.
private const val GRAVITY = 1.8

// Added to every post's age, so a post shared a moment ago doesn't divide by almost nothing.
private const val AGE_OFFSET_HOURS = 2.0

private val MILLIS_PER_HOUR = HOURS.toMillis(1).toDouble()

// Counted on top of a post's likes, so a new post with none still ranks above old liked ones
// until it ages. Hacker News doesn't need this, since it lists new stories apart.
private const val LIKES_OFFSET = 1

/**
 * How high a post ranks in the feed's Best order, by the Hacker News formula: its likes plus one
 * divided by its age in hours plus two, raised to the power 1.8. Likes lift a post and age sinks
 * it, so a new post with a few likes outranks an old one with many, and a new post with none
 * outranks an old one with a few.
 *
 * @param likeCount How many users liked the post.
 * @param ageMillis How long ago the post was shared. A negative age, from a phone whose clock is
 *     behind, counts as zero.
 */
fun bestScoreOf(likeCount: Int, ageMillis: Long): Double {
    val ageHours = ageMillis.coerceAtLeast(0) / MILLIS_PER_HOUR
    return (likeCount + LIKES_OFFSET) / (ageHours + AGE_OFFSET_HOURS).pow(GRAVITY)
}
