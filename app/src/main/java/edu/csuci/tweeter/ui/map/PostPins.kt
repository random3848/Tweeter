package edu.csuci.tweeter.ui.map

import androidx.annotation.VisibleForTesting
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.data.PostTable
import kotlin.math.cos
import kotlinx.serialization.json.contentOrNull
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

internal val PIN_RADIUS = 7.dp
internal val PIN_OUTLINE_WIDTH = 2.dp

// The selected post's pin is drawn bigger, over the others.
internal val SELECTED_PIN_RADIUS = 10.dp
internal val SELECTED_PIN_OUTLINE_WIDTH = 3.dp

// A tap this close to a pin still selects it, so that the small pins are easy to hit.
private val PIN_HIT_PADDING = 16.dp

/**
 * One pin per post of [uiState], declared in a map's style content, with the selected post's pin
 * drawn bigger over the others. A tap near several pins selects the one nearest to it.
 *
 * @param uiState The posts, their pins and the selected one.
 * @param onPinClick Called with the ID of the post whose pin the user taps.
 */
@Composable
@MaplibreComposable
internal fun PostPins(uiState: MapUiState, onPinClick: (String) -> Unit) {
    CircleLayer(
        id = "post-pins",
        source = rememberGeoJsonSource(uiState.pinnedPosts.pins),
        color = const(MaterialTheme.colorScheme.primary),
        radius = const(PIN_RADIUS),
        strokeColor = const(MaterialTheme.colorScheme.surface),
        strokeWidth = const(PIN_OUTLINE_WIDTH),
        onClick = { tappedPins ->
            val postId = position?.let { tap -> nearestPinnedPostId(tappedPins, tap) }
            if (postId == null) {
                ClickResult.Pass
            } else {
                onPinClick(postId)
                ClickResult.Consume
            }
        },
        hitPadding = PIN_HIT_PADDING
    )
    CircleLayer(
        id = "selected-post-pin",
        source = rememberGeoJsonSource(uiState.selectedPin),
        color = const(MaterialTheme.colorScheme.primary),
        radius = const(SELECTED_PIN_RADIUS),
        strokeColor = const(MaterialTheme.colorScheme.surface),
        strokeWidth = const(SELECTED_PIN_OUTLINE_WIDTH)
    )
}

/**
 * Returns the ID of the post whose pin, among [pins], is drawn nearest to [tap], or null if none
 * of them is a post's pin. Each pin carries its post's ID and position, so this reads only the
 * tapped pins, not the posts, and a pin drawn from an earlier table still names its post.
 */
@VisibleForTesting
internal fun nearestPinnedPostId(pins: List<Feature<*, *>>, tap: Position): String? {
    var nearestPostId: String? = null
    var nearestDistance = Double.POSITIVE_INFINITY
    for (pin in pins) {
        val postId = pin.id?.contentOrNull ?: continue
        val position = (pin.geometry as? Point)?.coordinates ?: continue
        val distance = squaredDistanceOnMap(position, tap)
        if (distance < nearestDistance) {
            nearestPostId = postId
            nearestDistance = distance
        }
    }
    return nearestPostId
}

/**
 * Returns where the pin of the post in [row] is drawn, where its photo was taken, or null if the
 * post has no pin because its coordinates can't be pinned.
 */
internal fun PostTable.pinPositionOf(row: Int): Position? {
    val latitude = latitude(row)
    val longitude = longitude(row)
    return if (isPinnable(latitude, longitude)) {
        Position(longitude = longitude, latitude = latitude)
    } else {
        null
    }
}

// How far from [tap] a pin at [position] is drawn on the map, squared and in degrees of
// latitude. Away from the equator, a degree of longitude is drawn shorter than one of latitude.
private fun squaredDistanceOnMap(position: Position, tap: Position): Double {
    val east = (position.longitude - tap.longitude) * cos(Math.toRadians(tap.latitude))
    val north = position.latitude - tap.latitude
    return east * east + north * north
}
