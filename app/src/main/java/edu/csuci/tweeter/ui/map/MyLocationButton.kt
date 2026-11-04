package edu.csuci.tweeter.ui.map

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import edu.csuci.tweeter.R
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.LocationTrackingStatus
import org.maplibre.compose.location.LocationUnavailableReason
import org.maplibre.compose.location.SystemSettingsLauncher
import org.maplibre.compose.location.rememberSystemSettingsLauncher

/**
 * The button that moves the map to the user's location, which the map shows as a blue dot.
 *
 * The app asks for location permission when the button is first pressed, not when the map opens.
 * If the user turned the permission down for good, the button opens the app's settings, where
 * they can allow it; if location is off, it opens the location settings. Either way, the map moves
 * to the user once their location arrives.
 *
 * @param locationState The user's location, and whether the app may get it.
 * @param onClick Moves the map to the user once their location is known.
 * @param modifier Applied to the button.
 */
@Composable
internal fun MyLocationButton(
    locationState: LocationState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsLauncher = rememberSystemSettingsLauncher()
    SmallFloatingActionButton(
        onClick = {
            onClick()
            askForMissingLocationAccess(locationState, settingsLauncher)
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

/**
 * Asks the user for what the app still needs to find them: location permission, or location
 * turned on. Does nothing when it has both.
 */
private fun askForMissingLocationAccess(
    locationState: LocationState,
    settingsLauncher: SystemSettingsLauncher
) {
    val permission = locationState.permission
    when {
        permission.isDeniedForGood() -> settingsLauncher.openApplicationSettings()
        permission !is LocationPermission.Granted -> locationState.requestPermission()
        locationState.status.isLocationOff() -> settingsLauncher.openLocationServicesSettings()
    }
}

// Android no longer shows the permission dialog, so only the app's settings can grant it.
private fun LocationPermission.isDeniedForGood(): Boolean =
    this is LocationPermission.NotGranted && canRequest == false

private fun LocationTrackingStatus.isLocationOff(): Boolean =
    this is LocationTrackingStatus.Unavailable &&
        reason == LocationUnavailableReason.ServicesDisabled
