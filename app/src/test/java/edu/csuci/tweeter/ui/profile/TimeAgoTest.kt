package edu.csuci.tweeter.ui.profile

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeAgoTest {
    private val now = Instant.ofEpochSecond(1_760_000_000)

    @Test
    fun timeAgoOf_underOneMinute_justNow() {
        assertEquals(TimeAgo(0, TimeAgoUnit.JUST_NOW), timeAgoOf(ago(Duration.ofSeconds(59)), now))
    }

    @Test
    fun timeAgoOf_future_justNow() {
        assertEquals(TimeAgo(0, TimeAgoUnit.JUST_NOW), timeAgoOf(now.plusSeconds(600), now))
    }

    @Test
    fun timeAgoOf_minutes_roundsDown() {
        assertEquals(
            TimeAgo(12, TimeAgoUnit.MINUTES),
            timeAgoOf(ago(Duration.ofSeconds(12 * 60 + 59)), now)
        )
    }

    @Test
    fun timeAgoOf_exactlyAnHour_hours() {
        assertEquals(TimeAgo(1, TimeAgoUnit.HOURS), timeAgoOf(ago(Duration.ofHours(1)), now))
    }

    @Test
    fun timeAgoOf_sixDays_days() {
        assertEquals(TimeAgo(6, TimeAgoUnit.DAYS), timeAgoOf(ago(Duration.ofDays(6)), now))
    }

    @Test
    fun timeAgoOf_twentyDays_weeks() {
        assertEquals(TimeAgo(2, TimeAgoUnit.WEEKS), timeAgoOf(ago(Duration.ofDays(20)), now))
    }

    private fun ago(duration: Duration): Instant = now.minus(duration)
}
