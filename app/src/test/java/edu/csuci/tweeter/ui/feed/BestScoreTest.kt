package edu.csuci.tweeter.ui.feed

import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val ONE_HOUR_MILLIS = HOURS.toMillis(1)
private val TWO_DAYS_MILLIS = HOURS.toMillis(48)
private val ONE_MINUTE_MILLIS = MINUTES.toMillis(1)
private val THIRTY_DAYS_MILLIS = DAYS.toMillis(30)

class BestScoreTest {
    @Test
    fun bestScoreOf_sameAge_moreLikesScoresHigher() {
        assertTrue(bestScoreOf(5, ONE_HOUR_MILLIS) > bestScoreOf(4, ONE_HOUR_MILLIS))
    }

    @Test
    fun bestScoreOf_sameLikes_olderScoresLower() {
        assertTrue(bestScoreOf(5, TWO_DAYS_MILLIS) < bestScoreOf(5, ONE_HOUR_MILLIS))
    }

    @Test
    fun bestScoreOf_newPostWithFewLikes_outranksOldPostWithMany() {
        assertTrue(bestScoreOf(3, ONE_HOUR_MILLIS) > bestScoreOf(10, TWO_DAYS_MILLIS))
    }

    @Test
    fun bestScoreOf_newPostWithNoLikes_outranksOldPostWithOne() {
        assertTrue(bestScoreOf(0, ONE_MINUTE_MILLIS) > bestScoreOf(1, THIRTY_DAYS_MILLIS))
    }

    @Test
    fun bestScoreOf_sameAge_noLikesScoresLowerThanOne() {
        assertTrue(bestScoreOf(0, ONE_HOUR_MILLIS) < bestScoreOf(1, ONE_HOUR_MILLIS))
    }

    @Test
    fun bestScoreOf_postFromTheFuture_scoresAsJustShared() {
        // A phone whose clock is behind sees posts shared after its "now".
        assertEquals(bestScoreOf(5, 0), bestScoreOf(5, -TWO_DAYS_MILLIS), 0.0)
    }
}
