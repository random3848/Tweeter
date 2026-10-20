package edu.csuci.tweeter.ui.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.csuci.tweeter.R
import edu.csuci.tweeter.location.DeviceLocationProvider
import edu.csuci.tweeter.location.GeoPoint
import edu.csuci.tweeter.ui.map.CAMPUS_LATITUDE
import edu.csuci.tweeter.ui.map.CAMPUS_LONGITUDE
import edu.csuci.tweeter.ui.map.CAMPUS_ZOOM
import edu.csuci.tweeter.ui.map.MAP_STYLE_URI
import edu.csuci.tweeter.ui.map.MapControls
import edu.csuci.tweeter.ui.map.PIN_OUTLINE_WIDTH
import edu.csuci.tweeter.ui.map.PIN_RADIUS
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

// How close the map zooms in on a picked point it moves to.
private const val FOCUS_ZOOM = 17.0

/**
 * A map for choosing where a photo was taken, with a "Use my location" button below it.
 *
 * Tapping the map picks that point. "Use my location" asks for location permission if needed
 * and picks the device's current position. The caller holds the picked point, so it can start
 * from a location found by `PhotoLocator`; the map moves to any point not picked by tapping.
 *
 * @param selected The picked point, drawn as a pin, or null if nothing is picked yet.
 * @param onSelect Called with the point the user picks.
 * @param modifier Applied to the whole picker.
 */
@Composable
fun LocationPicker(
    selected: GeoPoint?,
    onSelect: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceLocationProvider = remember { DeviceLocationProvider(context) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    var tappedPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var hasNoFix by remember { mutableStateOf(false) }

    fun pickDeviceLocation() {
        scope.launch {
            isLocating = true
            hasNoFix = false
            val fix = deviceLocationProvider.currentFix()
            isLocating = false
            if (fix == null) {
                hasNoFix = true
            } else {
                currentOnSelect(fix.point)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) {
        // Without permission, currentFix() returns null and the message below is shown.
        pickDeviceLocation()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PinMap(
            pin = selected,
            // Follow points picked any way but tapping, which would make the map jump under
            // the finger.
            cameraTarget = selected.takeIf { it != tappedPoint },
            onMapClick = {
                hasNoFix = false
                tappedPoint = it
                currentOnSelect(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        if (hasNoFix) {
            Text(
                text = stringResource(R.string.location_picker_no_fix),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Button(
            onClick = {
                if (deviceLocationProvider.hasPermission()) {
                    pickDeviceLocation()
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            enabled = !isLocating
        ) {
            Text(stringResource(R.string.location_picker_use_my_location))
        }
    }
}

/**
 * The app's map with one pin. It opens over the campus, or over [cameraTarget] if there is one.
 *
 * @param pin Where the pin is drawn, or null for no pin.
 * @param cameraTarget Where to move the map whenever this changes, or null to stay put.
 * @param onMapClick Called with the point the user taps.
 * @param modifier Applied to the map.
 */
@Composable
private fun PinMap(
    pin: GeoPoint?,
    cameraTarget: GeoPoint?,
    onMapClick: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val pinColor = MaterialTheme.colorScheme.primary
    val pinOutlineColor = MaterialTheme.colorScheme.surface
    val pinData = remember(pin) { GeoJsonData.Features(pin.toPins()) }
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(MAP_STYLE_URI),
        initialCameraPosition = cameraTarget?.let {
            CameraPosition(center = it.toPosition(), zoom = FOCUS_ZOOM)
        } ?: CameraPosition(
            center = Position(longitude = CAMPUS_LONGITUDE, latitude = CAMPUS_LATITUDE),
            zoom = CAMPUS_ZOOM
        )
    ) {
        CircleLayer(
            id = "picked-location",
            source = rememberGeoJsonSource(pinData),
            color = const(pinColor),
            radius = const(PIN_RADIUS),
            strokeColor = const(pinOutlineColor),
            strokeWidth = const(PIN_OUTLINE_WIDTH)
        )
    }
    val interactions = remember {
        MapInteractions(MapInteractions.Standard) {
            callbacks {
                click {
                    onEvent { event ->
                        val position = event.position
                        if (position == null) {
                            ClickResult.Pass
                        } else {
                            currentOnMapClick(position.toGeoPoint())
                            ClickResult.Consume
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        mapState.animateCamera(CameraUpdate(center = target.toPosition(), zoom = FOCUS_ZOOM))
    }

    MaplibreMap(
        modifier = modifier,
        state = mapState,
        interactions = interactions,
        overlay = { MapControls(mapState) }
    )
}

/** Converts this point to a GeoJSON collection of one point, or of none if it is null. */
private fun GeoPoint?.toPins(): FeatureCollection<Point, JsonObject?> = FeatureCollection(
    listOfNotNull(this).map { Feature(geometry = Point(it.toPosition()), properties = null) }
)

private fun GeoPoint.toPosition() = Position(longitude = longitude, latitude = latitude)

private fun Position.toGeoPoint() = GeoPoint(latitude = latitude, longitude = longitude)
