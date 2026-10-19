package edu.csuci.tweeter.ui.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import edu.csuci.tweeter.R
import edu.csuci.tweeter.location.DeviceLocationProvider
import edu.csuci.tweeter.location.GeoPoint
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** CSU Channel Islands, where the map opens when no location is picked yet. */
private val CAMPUS_CENTER = GeoPoint(34.1614, -119.0434)

private const val DEFAULT_ZOOM = 16.0
private const val FOCUS_ZOOM = 18.0
private const val PIN_ID = "pin"
private const val PIN_RADIUS = 8f
private const val PIN_STROKE_WIDTH = 2f

// OpenStreetMap's raster tiles, with the attribution their usage policy requires.
private const val OSM_STYLE_JSON = """
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "maxzoom": 19,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [{ "id": "osm", "type": "raster", "source": "osm" }]
}
"""

/**
 * A map for choosing where a photo was taken.
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
    var cameraTarget by remember { mutableStateOf(selected) }
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

    // Follow points picked any way but tapping, which would make the map jump under the finger.
    LaunchedEffect(selected) {
        if (selected != null && selected != tappedPoint) {
            cameraTarget = selected
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) {
        // Without permission, currentFix() returns null and the message below is shown.
        pickDeviceLocation()
    }

    Box(modifier = modifier) {
        PinMap(
            pin = selected,
            cameraTarget = cameraTarget,
            onMapClick = {
                hasNoFix = false
                tappedPoint = it
                currentOnSelect(it)
            },
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasNoFix) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.location_picker_no_fix),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
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
}

/**
 * A MapLibre map of OpenStreetMap tiles with one pin.
 *
 * @param pin Where the pin is drawn, or null for no pin.
 * @param cameraTarget Where to move the map whenever this changes, or null to stay on campus.
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
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val pinColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val pinStrokeColor = MaterialTheme.colorScheme.onTertiary.toArgb()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                mapView.onPause()
            }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                mapView.onStop()
            }
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { loadedMap ->
            loadedMap.cameraPosition = CameraPosition.Builder()
                .target((cameraTarget ?: CAMPUS_CENTER).toLatLng())
                .zoom(if (cameraTarget == null) DEFAULT_ZOOM else FOCUS_ZOOM)
                .build()
            loadedMap.addOnMapClickListener { latLng ->
                currentOnMapClick(GeoPoint(latLng.latitude, latLng.longitude))
                true
            }
            loadedMap.setStyle(Style.Builder().fromJson(OSM_STYLE_JSON)) { loadedStyle ->
                loadedStyle.addSource(GeoJsonSource(PIN_ID))
                loadedStyle.addLayer(
                    CircleLayer(PIN_ID, PIN_ID).withProperties(
                        PropertyFactory.circleColor(pinColor),
                        PropertyFactory.circleRadius(PIN_RADIUS),
                        PropertyFactory.circleStrokeColor(pinStrokeColor),
                        PropertyFactory.circleStrokeWidth(PIN_STROKE_WIDTH)
                    )
                )
                style = loadedStyle
            }
            map = loadedMap
        }
    }

    LaunchedEffect(style, pin) {
        val source = style?.getSourceAs<GeoJsonSource>(PIN_ID) ?: return@LaunchedEffect
        if (pin == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            source.setGeoJson(Point.fromLngLat(pin.longitude, pin.latitude))
        }
    }

    LaunchedEffect(map, cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), FOCUS_ZOOM))
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
