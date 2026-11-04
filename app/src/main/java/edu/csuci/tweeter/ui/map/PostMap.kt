package edu.csuci.tweeter.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.MapState
import org.maplibre.compose.style.BaseStyle

/**
 * The map engine that Map shows: OpenStreetMap's style with the layers the presenting screen
 * declares.
 *
 * [MapViewModel] keeps it while Map is open or in the back stack, so Map opened again presents the
 * map it left, with its style and tiles already loaded, instead of creating and loading another.
 * Only the screen around it is composed again.
 *
 * @param camera Where the map opens.
 */
internal class PostMap(camera: CameraPosition) : AutoCloseable {
    // The layers drawn over the style. MapLibre composes them only while a screen presents state.
    private var layers: @Composable () -> Unit by mutableStateOf({})

    /** The map to present with `MaplibreMap`. */
    val state: MapState = DefaultMapRuntime.instance.createMapState(
        baseStyle = BaseStyle.Uri(MAP_STYLE_URI),
        cameraPosition = camera
    ) {
        layers()
    }

    /** Draws [layers] over the style, in place of the layers drawn before. */
    fun drawLayers(layers: @Composable () -> Unit) {
        this.layers = layers
    }

    /** Draws no layers over the style from now on. */
    fun clearLayers() {
        layers = {}
    }

    override fun close() {
        state.close()
    }
}
