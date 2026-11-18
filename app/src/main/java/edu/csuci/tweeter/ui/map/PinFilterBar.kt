package edu.csuci.tweeter.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R
import org.maplibre.compose.overlay.MapOverlay

/**
 * The choice of whose posts the map pins: one segmented button per [PinFilter], with [chosen]
 * selected, above a message with a retry button if the map couldn't read the user's friends.
 *
 * @param chosen The filter whose posts the map pins.
 * @param friendsUnknown Whether the map couldn't read the user's friends.
 * @param onChoose Called with the filter the user presses.
 * @param onRetryFriends Called when the user asks to read their friends again.
 * @param modifier Applied to the bar.
 */
@Composable
internal fun PinFilterBar(
    chosen: PinFilter,
    friendsUnknown: Boolean,
    onChoose: (PinFilter) -> Unit,
    onRetryFriends: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MapOverlay.Spacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SingleChoiceSegmentedButtonRow {
            val filters = PinFilter.entries
            // Unselected segments are see-through by default, which over the map is unreadable.
            val colors = SegmentedButtonDefaults.colors(
                inactiveContainerColor = MaterialTheme.colorScheme.surface
            )
            filters.forEachIndexed { index, filter ->
                SegmentedButton(
                    selected = filter == chosen,
                    onClick = { onChoose(filter) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = filters.size),
                    colors = colors,
                    label = { Text(stringResource(filter.label)) }
                )
            }
        }
        if (friendsUnknown) {
            FriendsUnknownMessage(onRetry = onRetryFriends)
        }
    }
}

@Composable
private fun FriendsUnknownMessage(onRetry: () -> Unit) {
    Surface(
        // Screen readers announce the message as it appears.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.map_friends_unknown),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.map_friends_retry))
            }
        }
    }
}
