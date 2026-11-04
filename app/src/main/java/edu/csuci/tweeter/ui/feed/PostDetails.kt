package edu.csuci.tweeter.ui.feed

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import edu.csuci.tweeter.R
import edu.csuci.tweeter.ui.profile.timeAgoText

/**
 * Returns where a post was shared and how long ago, such as "Channel Islands · 5 minutes ago", or
 * only how long ago when the post names no place.
 *
 * @param locationName The name of the place the post was shared from; blank when it names none.
 * @param timestampMillis When the post was shared, in milliseconds since the epoch.
 */
@Composable
fun postDetailsText(locationName: String, timestampMillis: Long): String {
    val timeAgo = timeAgoText(timestampMillis)
    return if (locationName.isNotBlank()) {
        stringResource(R.string.post_details, locationName, timeAgo)
    } else {
        timeAgo
    }
}
