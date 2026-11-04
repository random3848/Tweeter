package edu.csuci.tweeter.ui.map

import androidx.annotation.VisibleForTesting
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.data.PostRef
import edu.csuci.tweeter.data.PostTable
import kotlin.math.cos
import kotlinx.serialization.json.doubleOrNull
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
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
    val posts = uiState.posts
    CircleLayer(
        id = "post-pins",
        source = rememberGeoJsonSource(uiState.pins.geoJson),
        color = const(MaterialTheme.colorScheme.primary),
        radius = const(PIN_RADIUS),
        strokeColor = const(MaterialTheme.colorScheme.surface),
        strokeWidth = const(PIN_OUTLINE_WIDTH),
        onClick = { tappedPins ->
            val tappedRow = position?.let { tap -> posts.nearestPinnedRow(tappedPins, tap) }
            if (tappedRow == null) {
                ClickResult.Pass
            } else {
                onPinClick(posts.id(tappedRow))
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
 * Returns the row of the post whose pin, among [pins], is drawn nearest to [tap], or null if none
 * of [pins] points into this table. Pins drawn from an earlier table point into none.
 */
@VisibleForTesting
internal fun PostTable.nearestPinnedRow(pins: List<Feature<*, *>>, tap: Position): Int? {
    var nearestRow: Int? = null
    var nearestDistance = Double.POSITIVE_INFINITY
    for (pin in pins) {
        val row = rowOf(pin) ?: continue
        val distance = squaredDistanceOnMap(row, tap)
        if (distance < nearestDistance) {
            nearestRow = row
            nearestDistance = distance
        }
    }
    return nearestRow
}

/** Returns where the photo of the post in [row] was taken, as a map position. */
internal fun PostTable.positionOf(row: Int) =
    Position(longitude = longitude(row), latitude = latitude(row))

// The row that the pin's ID, a PostRef, points to in this table. MapLibre may hand a numeric ID
// back as a double; a PostRef fits in one exactly.
private fun PostTable.rowOf(pin: Feature<*, *>): Int? =
    pin.id?.doubleOrNull?.let { rowOf(PostRef.fromLong(it.toLong())) }

// How far from the tap the pin of the row is drawn on the map, squared and in degrees of latitude.
// Away from the equator, a degree of longitude is drawn shorter than a degree of latitude.
private fun PostTable.squaredDistanceOnMap(row: Int, tap: Position): Double {
    val east = (longitude(row) - tap.longitude) * cos(Math.toRadians(tap.latitude))
    val north = latitude(row) - tap.latitude
    return east * east + north * north
}
