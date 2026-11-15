package edu.csuci.tweeter.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import edu.csuci.tweeter.R
import java.util.concurrent.TimeUnit

private val MILLIS_PER_MINUTE = TimeUnit.MINUTES.toMillis(1)
private val MILLIS_PER_HOUR = TimeUnit.HOURS.toMillis(1)
private val MILLIS_PER_DAY = TimeUnit.DAYS.toMillis(1)
private val MILLIS_PER_WEEK = TimeUnit.DAYS.toMillis(7)

/**
 * How long ago something happened, counted in the largest unit that fits, such as 12 minutes or
 * 3 days.
 *
 * @property amount How many whole [unit]s ago; 0 for [TimeAgoUnit.JUST_NOW].
 * @property unit What [amount] counts.
 */
data class TimeAgo(val amount: Long, val unit: TimeAgoUnit)

/** The units a [TimeAgo] counts in. */
enum class TimeAgoUnit {
    /** Less than a minute ago, or in the future because of a wrong clock. */
    JUST_NOW,

    /** At least a minute but less than an hour ago. */
    MINUTES,

    /** At least an hour but less than a day ago. */
    HOURS,

    /** At least a day but less than a week ago. */
    DAYS,

    /** A week or more ago. */
    WEEKS
}

/** Returns how long before [nowMillis] the moment [thenMillis] was, both since the epoch. */
fun timeAgoOf(thenMillis: Long, nowMillis: Long): TimeAgo {
    val elapsedMillis = nowMillis - thenMillis
    return when {
        elapsedMillis < MILLIS_PER_MINUTE -> TimeAgo(0, TimeAgoUnit.JUST_NOW)

        elapsedMillis < MILLIS_PER_HOUR -> {
            TimeAgo(elapsedMillis / MILLIS_PER_MINUTE, TimeAgoUnit.MINUTES)
        }

        elapsedMillis < MILLIS_PER_DAY -> {
            TimeAgo(
                elapsedMillis / MILLIS_PER_HOUR,
                TimeAgoUnit.HOURS
            )
        }

        elapsedMillis < MILLIS_PER_WEEK -> TimeAgo(elapsedMillis / MILLIS_PER_DAY, TimeAgoUnit.DAYS)

        else -> TimeAgo(elapsedMillis / MILLIS_PER_WEEK, TimeAgoUnit.WEEKS)
    }
}

/**
 * Returns how long ago [thenMillis] was as text, such as "12 minutes ago".
 *
 * @param thenMillis The moment, in milliseconds since the epoch.
 * @param nowMillis The current time, in milliseconds since the epoch.
 */
@Composable
fun timeAgoText(thenMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val timeAgo = timeAgoOf(thenMillis, nowMillis)
    val amount = timeAgo.amount.toInt()
    val plurals = when (timeAgo.unit) {
        TimeAgoUnit.JUST_NOW -> return stringResource(R.string.time_ago_just_now)
        TimeAgoUnit.MINUTES -> R.plurals.time_ago_minutes
        TimeAgoUnit.HOURS -> R.plurals.time_ago_hours
        TimeAgoUnit.DAYS -> R.plurals.time_ago_days
        TimeAgoUnit.WEEKS -> R.plurals.time_ago_weeks
    }
    return pluralStringResource(plurals, amount, amount)
}
