package edu.csuci.tweeter.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.data.Post
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.rememberDefaultHeadingProvider
import org.maplibre.compose.location.rememberLocationState
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
import org.maplibre.spatialk.geojson.Position

// OpenStreetMap's standard map, from its own tile servers. The style also holds the attribution
// that OpenStreetMap requires.
internal const val MAP_STYLE_URI = "file:///android_asset/openstreetmap-style.json"

// The map opens over the CSU Channel Islands campus buildings.
internal const val CAMPUS_LATITUDE = 34.1625
internal const val CAMPUS_LONGITUDE = -119.0440
internal const val CAMPUS_ZOOM = 15.0

// The map zooms in at least this close when it moves to a post or to the user.
private const val CLOSE_UP_ZOOM = 16.0

/**
 * The map: every post as a pin where its photo was taken, and the user's location as a blue dot.
 * It opens over the campus.
 *
 * Tapping a pin opens a popup above it with the post's photo, and the map moves to the pin. Back,
 * or a tap anywhere else on the map, closes the popup; pressing it shows the post on Home. Feed
 * shows a post here through [edu.csuci.tweeter.AppContainer.showOnMapRequest]. The location
 * button moves the map to the user, asking for location permission first if the app doesn't
 * have it.
 *
 * @param onShowInFeed Opens Home, once the post whose popup the user pressed is requested there.
 * @param modifier Applied to the screen.
 * @param viewModel Supplies the posts, the open popup and where the map moves next.
 */
@Composable
fun MapScreen(
    onShowInFeed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locationState = rememberLocationState(headingProvider = rememberDefaultHeadingProvider())
    PostMapLayers(viewModel.map, uiState, locationState, onPinClick = viewModel::selectPost)
    val mapState = viewModel.map.state

    BackHandler(enabled = uiState.selectedPost != null, onBack = viewModel::clearSelection)
    MapMoveEffect(uiState, mapState, locationState, onMoveFinished = viewModel::finishMove)
    MaplibreMap(
        modifier = modifier.fillMaxSize(),
        state = mapState,
        interactions = rememberMapInteractions(onMapClick = viewModel::clearSelection),
        overlay = {
            MapControls(mapState) {
                MyLocationButton(
                    locationState = locationState,
                    onClick = viewModel::moveToUser,
                    modifier = Modifier.padding(MapOverlay.Spacing)
                )
            }
            uiState.selectedPost?.let { post ->
                PinnedPostPopup(
                    post = post,
                    onClick = {
                        viewModel.showInFeed(post)
                        onShowInFeed()
                    }
                )
            }
        }
    )
}

/**
 * Draws a pin for each post in [uiState], and the user's location, on [map] while this screen
 * presents it.
 *
 * @param map The map to draw on.
 * @param uiState The posts to pin and the selected one.
 * @param locationState The user's location, drawn as a blue dot.
 * @param onPinClick Called with the ID of the post whose pin the user taps.
 */
@Composable
private fun PostMapLayers(
    map: PostMap,
    uiState: MapUiState,
    locationState: LocationState,
    onPinClick: (String) -> Unit
) {
    val currentOnPinClick by rememberUpdatedState(onPinClick)
    // Set while composing, as rememberUpdatedState sets its value, rather than in a SideEffect:
    // MapLibre composes the layers of a map it presents before SideEffects run, and layers it
    // found missing then would disappear from the map for a frame.
    map.layers = {
        PostPins(
            posts = uiState.posts,
            selectedPost = uiState.selectedPost,
            onPinClick = { currentOnPinClick(it) }
        )
        LocationIndicatorLayer(id = "user-location", locationState = locationState)
    }
    // The map outlives this screen; it mustn't keep the screen's location updates.
    DisposableEffect(map) {
        onDispose { map.layers = {} }
    }
}

/**
 * Moves the map once to where [MapUiState.pendingMove] asks: to the selected post's pin once the
 * post has loaded, or to the user once their location is known.
 *
 * @param uiState The move to make, and the selected post it may be to.
 * @param mapState The map to move.
 * @param locationState The user's location.
 * @param onMoveFinished Called with the move once the camera has arrived.
 */
@Composable
private fun MapMoveEffect(
    uiState: MapUiState,
    mapState: MapState,
    locationState: LocationState,
    onMoveFinished: (MapMove) -> Unit
) {
    val currentOnMoveFinished by rememberUpdatedState(onMoveFinished)
    val move = uiState.pendingMove
    val selectedPost = uiState.selectedPost
    LaunchedEffect(move, selectedPost) {
        val pendingMove = move ?: return@LaunchedEffect
        val target = when (pendingMove) {
            is MapMove.ToPost -> selectedPost?.takeIf { it.id == pendingMove.postId }?.position()
            MapMove.ToUser -> locationState.awaitLocation()
        } ?: return@LaunchedEffect
        mapState.moveCloseTo(target)
        // Only once the camera has arrived: finishing changes this effect's keys, which cancels it.
        currentOnMoveFinished(pendingMove)
    }
}

/** Returns the user's location, waiting for it if it isn't known yet. */
private suspend fun LocationState.awaitLocation(): Position =
    snapshotFlow { lastLocation?.position }.filterNotNull().first()

/** Moves the camera to [position], zooming in to at least [CLOSE_UP_ZOOM]. */
private suspend fun MapState.moveCloseTo(position: Position) {
    animateCamera(
        CameraUpdate(center = position, zoom = maxOf(cameraPosition.zoom, CLOSE_UP_ZOOM))
    )
}

/** The map's standard gestures, with [onMapClick] called for taps that no pin takes. */
@Composable
private fun rememberMapInteractions(onMapClick: () -> Unit): MapInteractions {
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    return remember {
        MapInteractions(MapInteractions.Standard) {
            callbacks {
                click {
                    onUnhandled {
                        currentOnMapClick()
                        ClickResult.Pass
                    }
                }
            }
        }
    }
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

/** [PostPopup] for [post], placed so that its tail points down at the post's pin. */
@Composable
private fun MapOverlayScope.PinnedPostPopup(post: Post, onClick: () -> Unit) {
    PostPopup(
        post = post,
        onClick = onClick,
        modifier = Modifier
            .placedAt(post.position(), Alignment.BottomCenter)
            // The tail's tip touches the top of the selected pin.
            .padding(bottom = SELECTED_PIN_RADIUS + SELECTED_PIN_OUTLINE_WIDTH)
    )
}
