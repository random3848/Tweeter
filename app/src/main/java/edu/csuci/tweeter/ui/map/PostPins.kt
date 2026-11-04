package edu.csuci.tweeter.ui.map

import androidx.annotation.VisibleForTesting
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.data.Post
import kotlin.math.cos
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

// The pins' GeoJSON property that holds their post's ID.
private const val POST_ID_PROPERTY = "postId"

internal val PIN_RADIUS = 7.dp
internal val PIN_OUTLINE_WIDTH = 2.dp

// The selected post's pin is drawn bigger, over the others.
internal val SELECTED_PIN_RADIUS = 10.dp
internal val SELECTED_PIN_OUTLINE_WIDTH = 3.dp

// A tap this close to a pin still selects it, so that the small pins are easy to hit.
private val PIN_HIT_PADDING = 16.dp

/**
 * One pin per post in [posts], declared in a map's style content, with the selected post's pin
 * drawn bigger over the others. A tap near several pins selects the one nearest to it.
 *
 * @param posts The posts to pin.
 * @param selectedPost The post whose pin stands out, or null.
 * @param onPinClick Called with the ID of the post whose pin the user taps.
 */
@Composable
@MaplibreComposable
internal fun PostPins(posts: List<Post>, selectedPost: Post?, onPinClick: (String) -> Unit) {
    val pins = remember(posts) { GeoJsonData.Features(posts.toPins()) }
    val selectedPin = remember(selectedPost) {
        GeoJsonData.Features(listOfNotNull(selectedPost).toPins())
    }
    CircleLayer(
        id = "post-pins",
        source = rememberGeoJsonSource(pins),
        color = const(MaterialTheme.colorScheme.primary),
        radius = const(PIN_RADIUS),
        strokeColor = const(MaterialTheme.colorScheme.surface),
        strokeWidth = const(PIN_OUTLINE_WIDTH),
        onClick = { tappedPins ->
            val tappedPost = position?.let { tap -> posts.pinnedIn(tappedPins).nearestTo(tap) }
            if (tappedPost == null) {
                ClickResult.Pass
            } else {
                onPinClick(tappedPost.id)
                ClickResult.Consume
            }
        },
        hitPadding = PIN_HIT_PADDING
    )
    CircleLayer(
        id = "selected-post-pin",
        source = rememberGeoJsonSource(selectedPin),
        color = const(MaterialTheme.colorScheme.primary),
        radius = const(SELECTED_PIN_RADIUS),
        strokeColor = const(MaterialTheme.colorScheme.surface),
        strokeWidth = const(SELECTED_PIN_OUTLINE_WIDTH)
    )
}

/** Returns the post whose pin is drawn nearest to [tap], or null if there are no posts. */
@VisibleForTesting
internal fun List<Post>.nearestTo(tap: Position): Post? =
    minByOrNull { it.position().squaredDistanceOnMapTo(tap) }

/** Returns where this post's photo was taken, as a map position. */
internal fun Post.position() = Position(longitude = longitude, latitude = latitude)

/** Converts posts to GeoJSON points, each at the place its post's photo was taken. */
private fun List<Post>.toPins(): FeatureCollection<Point, JsonObject?> = FeatureCollection(
    map { post ->
        Feature(
            geometry = Point(post.position()),
            properties = JsonObject(mapOf(POST_ID_PROPERTY to JsonPrimitive(post.id)))
        )
    }
)

/** Returns the posts that have a pin among [pins]. */
private fun List<Post>.pinnedIn(pins: List<Feature<Geometry, JsonObject?>>): List<Post> {
    val pinnedIds = pins.mapNotNull { it.properties?.postId() }.toSet()
    return filter { it.id in pinnedIds }
}

/** Returns the ID of the post whose pin has these properties, or null if they have none. */
private fun JsonObject.postId(): String? = (get(POST_ID_PROPERTY) as? JsonPrimitive)?.contentOrNull

// How far apart two nearby positions are drawn on the map, squared and in degrees of latitude.
// Away from the equator, a degree of longitude is drawn shorter than a degree of latitude.
private fun Position.squaredDistanceOnMapTo(other: Position): Double {
    val east = (longitude - other.longitude) * cos(Math.toRadians(other.latitude))
    val north = latitude - other.latitude
    return east * east + north * north
}
