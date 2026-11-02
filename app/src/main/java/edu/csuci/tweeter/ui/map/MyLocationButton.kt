package edu.csuci.tweeter.ui.map

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import edu.csuci.tweeter.R
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.LocationTrackingStatus
import org.maplibre.compose.location.LocationUnavailableReason
import org.maplibre.compose.location.rememberSystemSettingsLauncher
import org.maplibre.compose.map.MapState
import org.maplibre.spatialk.geojson.Position

// The map zooms in at least this close on the user's location.
private const val USER_LOCATION_ZOOM = 16.0

/**
 * The button that moves the map to the user's location, which the map shows as a blue dot.
 *
 * The app asks for location permission when the button is first pressed, not when the map opens.
 * If the user turned the permission down for good, the button opens the app's settings, where
 * they can allow it; if location is off, it opens the location settings. Either way, the map moves
 * to the user once their location arrives.
 *
 * @param locationState The user's location, which the blue dot shows.
 * @param mapState The map to move.
 * @param modifier Applied to the button.
 */
@Composable
internal fun MyLocationButton(
    locationState: LocationState,
    mapState: MapState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val settingsLauncher = rememberSystemSettingsLauncher()
    // Whether the button was pressed while there was no location to move to yet. The map then
    // moves to the first location that arrives.
    var isWaitingForLocation by rememberSaveable { mutableStateOf(false) }

    LocationTrackingEffect(
        locationState = locationState,
        enabled = isWaitingForLocation,
        trackBearing = false
    ) {
        isWaitingForLocation = false
        mapState.moveToUser(currentLocation.position)
    }

    SmallFloatingActionButton(
        onClick = {
            val permission = locationState.permission
            val status = locationState.status
            val location = locationState.lastLocation
            when {
                permission is LocationPermission.NotGranted && permission.canRequest == false -> {
                    settingsLauncher.openApplicationSettings()
                }

                permission !is LocationPermission.Granted -> {
                    isWaitingForLocation = true
                    locationState.requestPermission()
                }

                status is LocationTrackingStatus.Unavailable &&
                    status.reason == LocationUnavailableReason.ServicesDisabled -> {
                    isWaitingForLocation = true
                    settingsLauncher.openLocationServicesSettings()
                }

                location != null -> scope.launch { mapState.moveToUser(location.position) }

                else -> isWaitingForLocation = true
            }
        },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_my_location),
            contentDescription = stringResource(R.string.map_my_location)
        )
    }
}

/** Moves the camera to the user at [position], zooming in to at least [USER_LOCATION_ZOOM]. */
private suspend fun MapState.moveToUser(position: Position) {
    animateCamera(
        CameraUpdate(center = position, zoom = maxOf(cameraPosition.zoom, USER_LOCATION_ZOOM))
    )
}
