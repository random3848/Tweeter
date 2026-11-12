package edu.csuci.tweeter.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.MapState
import org.maplibre.compose.style.BaseStyle

/**
 * The map engine that Map shows: OpenStreetMap's style, with a pin for each post and the user's
 * location over it.
 *
 * [MapViewModel] keeps it as long as the activity, rotations included, so [MapScreen] composed
 * again presents the map it left, with its style and tiles already loaded, instead of creating and
 * loading another.
 *
 * Its layers are declared once, here, and draw what the presenting screen last handed them with
 * [drawLayers]. They keep drawing that when the screen goes, so the screen that presents the map
 * next finds its pins in place from its first frame.
 *
 * @param camera Where the map opens.
 * @param onPinClick Called with the ID of the post whose pin the user taps.
 */
internal class PostMap(camera: CameraPosition, onPinClick: (postId: String) -> Unit) :
    AutoCloseable {
    private var uiState by mutableStateOf(MapUiState())

    private var locationState by mutableStateOf<LocationState?>(null)

    /** The map to present with `MaplibreMap`. */
    val state: MapState = DefaultMapRuntime.instance.createMapState(
        baseStyle = BaseStyle.Uri(MAP_STYLE_URI),
        cameraPosition = camera
    ) {
        PostPins(uiState, onPinClick)
        locationState?.let { LocationIndicatorLayer(id = "user-location", locationState = it) }
    }

    /** Draws the pins of [uiState], and the user's location from [locationState], from now on. */
    fun drawLayers(uiState: MapUiState, locationState: LocationState) {
        this.uiState = uiState
        this.locationState = locationState
    }

    override fun close() {
        state.close()
    }
}

/**
 * Starts the map engine that every [PostMap] shares, unless it has started: loads its native
 * libraries and opens its tile cache. Any thread may call it, once [configureMapTiles] has run.
 */
internal fun startMapEngine() {
    DefaultMapRuntime.instance
}
