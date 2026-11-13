package edu.csuci.tweeter.ui

import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.Assert.assertEquals
import org.junit.Test

private const val NOW_MILLIS = 1_760_000_000_000L

class TimeAgoTest {
    @Test
    fun timeAgoOf_underOneMinute_justNow() {
        assertEquals(
            TimeAgo(0, TimeAgoUnit.JUST_NOW),
            timeAgoOf(ago(MINUTES.toMillis(1) - 1), NOW_MILLIS)
        )
    }

    @Test
    fun timeAgoOf_exactlyOneMinute_minutes() {
        assertEquals(
            TimeAgo(1, TimeAgoUnit.MINUTES),
            timeAgoOf(ago(MINUTES.toMillis(1)), NOW_MILLIS)
        )
    }

    @Test
    fun timeAgoOf_future_justNow() {
        assertEquals(
            TimeAgo(0, TimeAgoUnit.JUST_NOW),
            timeAgoOf(NOW_MILLIS + MINUTES.toMillis(10), NOW_MILLIS)
        )
    }

    @Test
    fun timeAgoOf_minutes_roundsDown() {
        val twelveMinutesAndAlmostAnother = MINUTES.toMillis(12) + SECONDS.toMillis(59)

        assertEquals(
            TimeAgo(12, TimeAgoUnit.MINUTES),
            timeAgoOf(ago(twelveMinutesAndAlmostAnother), NOW_MILLIS)
        )
    }

    @Test
    fun timeAgoOf_exactlyAnHour_hours() {
        assertEquals(TimeAgo(1, TimeAgoUnit.HOURS), timeAgoOf(ago(HOURS.toMillis(1)), NOW_MILLIS))
    }

    @Test
    fun timeAgoOf_justUnderOneDay_hours() {
        assertEquals(
            TimeAgo(23, TimeAgoUnit.HOURS),
            timeAgoOf(ago(DAYS.toMillis(1) - 1), NOW_MILLIS)
        )
    }

    @Test
    fun timeAgoOf_sixDays_days() {
        assertEquals(TimeAgo(6, TimeAgoUnit.DAYS), timeAgoOf(ago(DAYS.toMillis(6)), NOW_MILLIS))
    }

    @Test
    fun timeAgoOf_exactlyOneWeek_weeks() {
        assertEquals(TimeAgo(1, TimeAgoUnit.WEEKS), timeAgoOf(ago(DAYS.toMillis(7)), NOW_MILLIS))
    }

    @Test
    fun timeAgoOf_twentyDays_weeks() {
        assertEquals(TimeAgo(2, TimeAgoUnit.WEEKS), timeAgoOf(ago(DAYS.toMillis(20)), NOW_MILLIS))
    }

    private fun ago(millis: Long): Long = NOW_MILLIS - millis
}
