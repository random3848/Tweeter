package edu.csuci.tweeter.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import edu.csuci.tweeter.R
import java.time.Duration
import java.time.Instant

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

/** Returns how long before [now] the moment [then] was. */
fun timeAgoOf(then: Instant, now: Instant): TimeAgo {
    val elapsed = Duration.between(then, now)
    return when {
        elapsed < Duration.ofMinutes(1) -> TimeAgo(0, TimeAgoUnit.JUST_NOW)
        elapsed < Duration.ofHours(1) -> TimeAgo(elapsed.toMinutes(), TimeAgoUnit.MINUTES)
        elapsed < Duration.ofDays(1) -> TimeAgo(elapsed.toHours(), TimeAgoUnit.HOURS)
        elapsed < Duration.ofDays(7) -> TimeAgo(elapsed.toDays(), TimeAgoUnit.DAYS)
        else -> TimeAgo(elapsed.toDays() / 7, TimeAgoUnit.WEEKS)
    }
}

/** Returns how long ago [then] was as text, such as "12 minutes ago". */
@Composable
fun timeAgoText(then: Instant, now: Instant = Instant.now()): String {
    val timeAgo = timeAgoOf(then, now)
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
