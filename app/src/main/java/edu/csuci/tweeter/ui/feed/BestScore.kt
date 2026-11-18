package edu.csuci.tweeter.ui.feed

import java.util.concurrent.TimeUnit.HOURS
import kotlin.math.pow

// How fast a post sinks as it ages: the age term is raised to this power, as on Hacker News.
private const val GRAVITY = 1.8

// Added to every post's age, so a post shared a moment ago doesn't divide by almost nothing.
private const val AGE_OFFSET_HOURS = 2.0

private val MILLIS_PER_HOUR = HOURS.toMillis(1).toDouble()

/**
 * How high a post ranks in the feed's Best order, by the Hacker News formula: its likes divided by
 * its age in hours plus two, raised to the power 1.8. Likes lift a post and age sinks it, so a new
 * post with a few likes outranks an old one with many.
 *
 * @param likeCount How many users liked the post.
 * @param ageMillis How long ago the post was shared. A negative age, from a phone whose clock is
 *     behind, counts as zero.
 */
fun bestScoreOf(likeCount: Int, ageMillis: Long): Double {
    val ageHours = ageMillis.coerceAtLeast(0) / MILLIS_PER_HOUR
    return likeCount / (ageHours + AGE_OFFSET_HOURS).pow(GRAVITY)
}
