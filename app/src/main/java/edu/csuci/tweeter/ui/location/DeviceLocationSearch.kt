package edu.csuci.tweeter.ui.location

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.csuci.tweeter.location.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The search for the device's location behind [LocationPicker]'s "Use my location" button.
 *
 * @param scope Where the search runs, on the main thread.
 * @param findDevicePoint Returns where the device is, or null if no fix comes.
 */
internal class DeviceLocationSearch(
    private val scope: CoroutineScope,
    private val findDevicePoint: suspend () -> GeoPoint?
) {
    /** Whether a search is running, during which the button is disabled. */
    var isLocating by mutableStateOf(false)
        private set

    /** Whether the last search found no fix, which the picker says. */
    var hasNoFix by mutableStateOf(false)
        private set

    /** Searches for the device's location, passing it to [onFound] if a fix comes. */
    fun start(onFound: (GeoPoint) -> Unit) {
        scope.launch {
            isLocating = true
            hasNoFix = false
            val point = findDevicePoint()
            isLocating = false
            if (point == null) {
                hasNoFix = true
            } else {
                onFound(point)
            }
        }
    }

    /** Stops saying that no fix was found, once the user picks a point on the map instead. */
    fun clearNoFix() {
        hasNoFix = false
    }
}
