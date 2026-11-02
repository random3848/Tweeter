package edu.csuci.tweeter.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.data.Post
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.overlay.AttributionLinks
import org.maplibre.compose.overlay.CompassButtonStyle
import org.maplibre.compose.overlay.DisappearingCompassButton
import org.maplibre.compose.overlay.DisappearingScaleBar
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.overlay.MapOverlayScope
import org.maplibre.compose.overlay.MaplibreLogo
import org.maplibre.compose.overlay.attributions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

// OpenStreetMap's standard map, from its own tile servers. The style also holds the attribution
// that OpenStreetMap requires.
internal const val MAP_STYLE_URI = "file:///android_asset/openstreetmap-style.json"

// The map opens over the CSU Channel Islands campus buildings.
internal const val CAMPUS_LATITUDE = 34.1625
internal const val CAMPUS_LONGITUDE = -119.0440
internal const val CAMPUS_ZOOM = 15.0

internal val PIN_RADIUS = 7.dp
internal val PIN_OUTLINE_WIDTH = 2.dp

/**
 * The map: every post as a pin where its photo was taken. It opens over the campus and stays as
 * the user left it while another tab is open.
 *
 * @param modifier Applied to the map.
 * @param viewModel Holds the map.
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory)
) {
    val mapState = viewModel.mapState
    MaplibreMap(
        modifier = modifier.fillMaxSize(),
        state = mapState,
        overlay = { MapControls(mapState) }
    )
}

/** One pin per post in [posts], declared in a map's style content. */
@Composable
@MaplibreComposable
internal fun PostPins(posts: List<Post>) {
    val pins = remember(posts) { GeoJsonData.Features(posts.toPins()) }
    CircleLayer(
        id = "post-pins",
        source = rememberGeoJsonSource(pins),
        color = const(MaterialTheme.colorScheme.primary),
        radius = const(PIN_RADIUS),
        strokeColor = const(MaterialTheme.colorScheme.surface),
        strokeWidth = const(PIN_OUTLINE_WIDTH)
    )
}

/**
 * MapLibre's default controls in the app's theme, except that the attribution stays on screen:
 * theirs collapses once the map moves, which OpenStreetMap doesn't allow.
 */
@Composable
internal fun MapOverlayScope.MapControls(mapState: MapState) {
    DisappearingScaleBar(
        metersPerDp = { mapState.viewport?.metersPerDpAtCenter ?: 0.0 },
        zoom = { mapState.cameraPosition.zoom },
        modifier = Modifier.align(Alignment.TopStart).padding(MapOverlay.Spacing),
        color = MaterialTheme.colorScheme.onSurface,
        haloColor = MaterialTheme.colorScheme.surface,
        textStyle = MaterialTheme.typography.labelSmall
    )
    DisappearingCompassButton(
        modifier = Modifier.align(Alignment.TopEnd).padding(MapOverlay.Spacing),
        style = CompassButtonStyle(containerColor = MaterialTheme.colorScheme.surface)
    )
    MaplibreLogo(Modifier.align(Alignment.BottomStart).padding(MapOverlay.Spacing))
    AttributionLinks(
        attributions = mapState.style.attributions(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        textStyle = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurface
        )
    )
}

/** Converts posts to GeoJSON points, each at the place its post's photo was taken. */
private fun List<Post>.toPins(): FeatureCollection<Point, JsonObject?> = FeatureCollection(
    map { post ->
        Feature(
            geometry = Point(Position(longitude = post.longitude, latitude = post.latitude)),
            properties = null
        )
    }
)
