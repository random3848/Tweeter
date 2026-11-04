package edu.csuci.tweeter.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csuci.tweeter.ui.navigation.TweeterTopBar
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
 * The map, under the app's top bar: every post as a pin where its photo was taken, and the user's
 * location as a blue dot. It opens over the campus.
 *
 * Tapping a pin opens a popup above it with the post's photo, and the map moves to the pin. Back,
 * or a tap anywhere else on the map, closes the popup; pressing it shows the post on Home. Feed
 * shows a post here through [edu.csuci.tweeter.AppContainer.showOnMapRequest]. The location
 * button moves the map to the user, asking for location permission first if the app doesn't
 * have it.
 *
 * The app keeps this screen composed under every other screen, so the map is drawn and ready the
 * moment the user switches to it. While hidden, it takes no touches, shows no controls and stops
 * following the user's location.
 *
 * @param isShown Whether the user is on Map, rather than on a screen drawn over the map.
 * @param onShowInFeed Opens Home, once the post whose popup the user pressed is requested there.
 * @param modifier Applied to the screen.
 * @param viewModel Supplies the posts, the open popup and where the map moves next.
 */
@Composable
fun MapScreen(
    isShown: Boolean,
    onShowInFeed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(factory = MapViewModel.factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locationState = rememberLocationState(
        headingProvider = rememberDefaultHeadingProvider(),
        enabled = isShown
    )
    PostMapLayers(viewModel, uiState, locationState)
    val mapState = viewModel.mapState

    if (isShown) {
        // Composed as Map opens, after the NavHost's own handler, so that it takes Back first.
        BackHandler(enabled = uiState.selectedRow != null, onBack = viewModel::clearSelection)
    }
    MapMoveEffect(uiState, mapState, locationState, onMoveFinished = viewModel::finishMove)
    Column(modifier = modifier.fillMaxSize()) {
        TweeterTopBar()
        MaplibreMap(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (isShown) Modifier else Modifier.consumingEveryTouch()),
            state = mapState,
            interactions = rememberMapInteractions(onMapClick = viewModel::clearSelection),
            overlay = {
                if (isShown) {
                    MapControls(mapState) {
                        MyLocationButton(
                            locationState = locationState,
                            onClick = viewModel::moveToUser,
                            modifier = Modifier.padding(MapOverlay.Spacing)
                        )
                    }
                    PinnedPostPopup(
                        uiState = uiState,
                        onClick = { postId ->
                            viewModel.showInFeed(postId)
                            onShowInFeed()
                        }
                    )
                }
            }
        )
    }
}

/**
 * Draws a pin for each post in [uiState], and the user's location, on the map of [viewModel]
 * while this screen presents it.
 *
 * A map composes the layer content it was created with, and this one outlives the screen, so its
 * content draws the layers that the presenting screen hands it here.
 *
 * @param viewModel Holds the map to draw on, and selects the post whose pin the user taps.
 * @param uiState The posts to pin and the selected one.
 * @param locationState The user's location, drawn as a blue dot.
 */
@Composable
private fun PostMapLayers(
    viewModel: MapViewModel,
    uiState: MapUiState,
    locationState: LocationState
) {
    // Set while composing, as rememberUpdatedState sets its value, rather than in a SideEffect:
    // MapLibre composes the layers of a map it presents before SideEffects run, and layers it
    // found missing then would disappear from the map for a frame.
    viewModel.drawLayers {
        PostPins(uiState, onPinClick = viewModel::selectPost)
        LocationIndicatorLayer(id = "user-location", locationState = locationState)
    }
    // The map outlives this screen, which goes with its activity; it mustn't keep the screen's
    // location state.
    DisposableEffect(viewModel) {
        onDispose { viewModel.clearLayers() }
    }
}

// Hidden under another screen, the map must not move or open popups for the touches that screen
// lets through: this takes them first, and the map ignores a touch taken before it.
private fun Modifier.consumingEveryTouch(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach(PointerInputChange::consume)
        }
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
    val selectedPostId = uiState.selectedRow?.let(uiState.posts::id)
    val selectedPosition = uiState.selectedPosition
    LaunchedEffect(move, selectedPosition) {
        val pendingMove = move ?: return@LaunchedEffect
        val target = when (pendingMove) {
            is MapMove.ToPost -> selectedPosition?.takeIf { selectedPostId == pendingMove.postId }
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

/**
 * [PostPopup] for the selected post of [uiState], placed so that its tail points down at the
 * post's pin, or nothing if no post is selected.
 *
 * @param uiState The posts and the selected one.
 * @param onClick Called with the selected post's ID when the user presses the popup.
 */
@Composable
private fun MapOverlayScope.PinnedPostPopup(uiState: MapUiState, onClick: (String) -> Unit) {
    val row = uiState.selectedRow ?: return
    val position = uiState.selectedPosition ?: return
    val posts = uiState.posts
    PostPopup(
        posts = posts,
        row = row,
        onClick = { onClick(posts.id(row)) },
        modifier = Modifier
            .placedAt(position, Alignment.BottomCenter)
            // The tail's tip touches the top of the selected pin.
            .padding(bottom = SELECTED_PIN_RADIUS + SELECTED_PIN_OUTLINE_WIDTH)
    )
}
