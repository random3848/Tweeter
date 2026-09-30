package edu.csuci.tweeter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import edu.csuci.tweeter.location.GeoPoint
import edu.csuci.tweeter.location.LocationFix
import kotlin.math.cos
import kotlin.math.sin
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** CSU Channel Islands, the default map center. */
val CAMPUS_CENTER = GeoPoint(34.1614, -119.0434)

private const val DEFAULT_ZOOM = 16.0
private const val FOCUS_ZOOM = 18.0
private const val METERS_PER_DEGREE_LAT = 111_320.0
private const val EXIF_COLOR = "#1E88E5"
private const val DEVICE_COLOR = "#43A047"
private const val MANUAL_COLOR = "#E53935"
private const val ACCURACY_ID = "device-accuracy"
private const val EXIF_ID = "exif"
private const val DEVICE_ID = "device"
private const val MANUAL_ID = "manual"

// Raster OpenStreetMap tiles. Fine for a low-volume test app; a production app should use a
// tile provider meant for app traffic (see https://operations.osmfoundation.org/policies/tiles/).
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
 * The points to draw on the map.
 *
 * @property exif the EXIF location (blue).
 * @property device the live device fix (green), drawn with its accuracy circle.
 * @property manual the user's pin (red).
 * @property focus where to move the camera, if anywhere.
 */
data class MapMarkers(
    val exif: GeoPoint? = null,
    val device: LocationFix? = null,
    val manual: GeoPoint? = null,
    val focus: GeoPoint? = null,
)

/**
 * A MapLibre map showing OpenStreetMap tiles and the test's location markers.
 *
 * @param markers the markers to draw.
 * @param onMapClick called with the tapped point, used to drop the manual pin.
 */
@Composable
fun OsmMap(
    markers: MapMarkers,
    onMapClick: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context) }
    val currentOnMapClick by rememberUpdatedState(onMapClick)
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
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { loadedMap ->
            loadedMap.cameraPosition = CameraPosition.Builder()
                .target(CAMPUS_CENTER.toLatLng())
                .zoom(DEFAULT_ZOOM)
                .build()
            loadedMap.addOnMapClickListener { latLng ->
                currentOnMapClick(GeoPoint(latLng.latitude, latLng.longitude))
                true
            }
            loadedMap.setStyle(Style.Builder().fromJson(OSM_STYLE_JSON)) { loadedStyle ->
                addMarkerLayers(loadedStyle)
                style = loadedStyle
            }
            map = loadedMap
        }
    }

    LaunchedEffect(style, markers) {
        style?.let { updateMarkers(it, markers) }
    }

    LaunchedEffect(map, markers.focus) {
        val target = markers.focus ?: return@LaunchedEffect
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), FOCUS_ZOOM))
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addMarkerLayers(style: Style) {
    style.addSource(GeoJsonSource(ACCURACY_ID))
    style.addLayer(
        FillLayer(ACCURACY_ID, ACCURACY_ID).withProperties(
            PropertyFactory.fillColor(DEVICE_COLOR),
            PropertyFactory.fillOpacity(0.2f),
            PropertyFactory.fillOutlineColor(DEVICE_COLOR),
        ),
    )
    val circles = listOf(
        EXIF_ID to EXIF_COLOR,
        DEVICE_ID to DEVICE_COLOR,
        MANUAL_ID to MANUAL_COLOR,
    )
    for ((id, color) in circles) {
        style.addSource(GeoJsonSource(id))
        style.addLayer(
            CircleLayer(id, id).withProperties(
                PropertyFactory.circleColor(color),
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )
    }
}

private fun updateMarkers(style: Style, markers: MapMarkers) {
    style.setPoint(EXIF_ID, markers.exif)
    style.setPoint(DEVICE_ID, markers.device?.point)
    style.setPoint(MANUAL_ID, markers.manual)
    val device = markers.device
    val radius = device?.accuracyMeters
    val accuracy = style.getSourceAs<GeoJsonSource>(ACCURACY_ID)
    if (device != null && radius != null) {
        accuracy?.setGeoJson(Feature.fromGeometry(circlePolygon(device.point, radius.toDouble())))
    } else {
        accuracy?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }
}

private fun Style.setPoint(sourceId: String, point: GeoPoint?) {
    val source = getSourceAs<GeoJsonSource>(sourceId) ?: return
    if (point == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    } else {
        source.setGeoJson(Point.fromLngLat(point.longitude, point.latitude))
    }
}

/** Approximates a circle of [radiusMeters] around [center]; plenty accurate at campus scale. */
private fun circlePolygon(center: GeoPoint, radiusMeters: Double, steps: Int = 64): Polygon {
    val dLat = radiusMeters / METERS_PER_DEGREE_LAT
    val dLng = radiusMeters / (METERS_PER_DEGREE_LAT * cos(Math.toRadians(center.latitude)))
    val ring = (0..steps).map { i ->
        val angle = 2 * Math.PI * i / steps
        Point.fromLngLat(center.longitude + dLng * cos(angle), center.latitude + dLat * sin(angle))
    }
    return Polygon.fromLngLats(listOf(ring))
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
