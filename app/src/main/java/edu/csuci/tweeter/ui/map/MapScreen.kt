package edu.csuci.tweeter.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.data.Post
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.rememberDefaultHeadingProvider
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
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
import org.maplibre.compose.style.BaseStyle
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

// The map zooms in at least this close on the selected post's pin.
private const val SELECTED_POST_ZOOM = 16.0

// The pins' GeoJSON property that holds their post's ID.
private const val POST_ID_PROPERTY = "postId"

internal val PIN_RADIUS = 7.dp
internal val PIN_OUTLINE_WIDTH = 2.dp

// The selected post's pin is drawn bigger, over the others.
private val SELECTED_PIN_RADIUS = 10.dp
private val SELECTED_PIN_OUTLINE_WIDTH = 3.dp

// A tap this close to a pin still selects it, so that the small pins are easy to hit.
private val PIN_HIT_PADDING = 16.dp

/**
 * The map: every post as a pin where its photo was taken, and the user's location as a blue dot.
 * It opens over the campus.
 *
 * Tapping a pin selects its post: the map moves to the pin, and the post's photo and details show
 * below the map until the user closes them or taps the map anywhere else. Other screens show a
 * post here by selecting it in [MapSelection] before opening Map. The location button moves the
 * map to the user, asking for location permission first if the app doesn't have it.
 *
 * @param modifier Applied to the screen.
 * @param viewModel Supplies the posts and the selected one.
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory)
) {
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val selectedPost by viewModel.selectedPost.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        PostMap(
            posts = posts,
            selectedPost = selectedPost,
            onPinClick = viewModel::selectPost,
            onMapClick = viewModel::clearSelection,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        selectedPost?.let { post ->
            SelectedPostCard(post = post, onClose = viewModel::clearSelection)
        }
    }
}

/**
 * A map with one pin per post in [posts], the user's location, and a button that moves to it.
 *
 * @param posts The posts to pin.
 * @param selectedPost The post whose pin stands out and that the map moves to, or null.
 * @param onPinClick Called with the ID of the post whose pin the user taps.
 * @param onMapClick Called when the user taps the map anywhere but on a pin.
 * @param modifier Applied to the map.
 */
@Composable
private fun PostMap(
    posts: List<Post>,
    selectedPost: Post?,
    onPinClick: (String) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnPinClick by rememberUpdatedState(onPinClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val locationState = rememberLocationState(headingProvider = rememberDefaultHeadingProvider())
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(MAP_STYLE_URI),
        initialCameraPosition = CameraPosition(
            center = Position(longitude = CAMPUS_LONGITUDE, latitude = CAMPUS_LATITUDE),
            zoom = CAMPUS_ZOOM
        )
    ) {
        PostPins(posts = posts, selectedPost = selectedPost, onPinClick = { currentOnPinClick(it) })
        LocationIndicatorLayer(id = "user-location", locationState = locationState)
    }
    val interactions = remember {
        MapInteractions(MapInteractions.Standard) {
            callbacks {
                click {
                    // Only taps that no pin took reach this.
                    onUnhandled {
                        currentOnMapClick()
                        ClickResult.Pass
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedPost?.id) {
        val post = selectedPost ?: return@LaunchedEffect
        mapState.animateCamera(
            CameraUpdate(
                center = post.position(),
                zoom = maxOf(mapState.cameraPosition.zoom, SELECTED_POST_ZOOM)
            )
        )
    }

    MaplibreMap(
        modifier = modifier,
        state = mapState,
        interactions = interactions,
        overlay = {
            MapControls(mapState) {
                MyLocationButton(
                    locationState = locationState,
                    mapState = mapState,
                    modifier = Modifier.padding(MapOverlay.Spacing)
                )
            }
        }
    )
}

/**
 * One pin per post in [posts], declared in a map's style content, with the selected post's pin
 * drawn bigger over the others.
 *
 * @param posts The posts to pin.
 * @param selectedPost The post whose pin stands out, or null.
 * @param onPinClick Called with the ID of the post whose pin the user taps.
 */
@Composable
@MaplibreComposable
private fun PostPins(posts: List<Post>, selectedPost: Post?, onPinClick: (String) -> Unit) {
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
        onClick = { features ->
            val postId = features.firstNotNullOfOrNull { it.properties?.postId() }
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
        source = rememberGeoJsonSource(selectedPin),
        color = const(MaterialTheme.colorScheme.primary),
        radius = const(SELECTED_PIN_RADIUS),
        strokeColor = const(MaterialTheme.colorScheme.surface),
        strokeWidth = const(SELECTED_PIN_OUTLINE_WIDTH)
    )
}

/**
 * MapLibre's default controls in the app's theme, except that the attribution stays on screen:
 * theirs collapses once the map moves, which OpenStreetMap doesn't allow.
 *
 * @param mapState The map the controls belong to.
 * @param actions The screen's own buttons, shown above the attribution in the bottom-right corner.
 */
@Composable
internal fun MapOverlayScope.MapControls(mapState: MapState, actions: @Composable () -> Unit = {}) {
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
    Column(modifier = Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
        actions()
        AttributionLinks(
            attributions = mapState.style.attributions(),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

/** Converts posts to GeoJSON points, each at the place its post's photo was taken. */
private fun List<Post>.toPins(): FeatureCollection<Point, JsonObject?> = FeatureCollection(
    map { post ->
        Feature(
            geometry = Point(post.position()),
            properties = JsonObject(mapOf(POST_ID_PROPERTY to JsonPrimitive(post.id)))
        )
    }
)

/** Returns the ID of the post whose pin has these properties, or null if they have none. */
private fun JsonObject.postId(): String? = (get(POST_ID_PROPERTY) as? JsonPrimitive)?.contentOrNull

/** Returns where this post's photo was taken, as a map position. */
private fun Post.position() = Position(longitude = longitude, latitude = latitude)
