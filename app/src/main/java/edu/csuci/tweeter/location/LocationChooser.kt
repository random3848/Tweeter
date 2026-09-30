package edu.csuci.tweeter.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The location picked for a photo, and why.
 *
 * @property fix the chosen fix, or null when the user needs to drop a pin manually.
 * @property reason a short human-readable explanation of the choice.
 */
data class LocationChoice(val fix: LocationFix?, val reason: String)

/** Picks the best location for a photo from the fixes available. */
object LocationChooser {

    /** Device fixes with an accuracy radius at or below this are trusted over EXIF. */
    const val MAX_TRUSTED_DEVICE_ACCURACY_METERS = 50f

    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /**
     * Chooses a location for a photo.
     *
     * A manual pin always wins. For a photo taken in the app, a live device fix is preferred when
     * it is accurate enough, then EXIF, then any device fix. For an imported photo only EXIF is
     * used, since the device's current position says nothing about where the photo was taken.
     *
     * @param exif the location read from the photo's EXIF metadata, if any.
     * @param device the live device fix, if any.
     * @param manual the pin the user dropped, if any.
     * @param capturedInApp true when the photo was just taken through the app's camera intent.
     */
    fun chooseBest(
        exif: LocationFix?,
        device: LocationFix?,
        manual: LocationFix?,
        capturedInApp: Boolean,
    ): LocationChoice {
        if (manual != null) {
            return LocationChoice(manual, "Manual pin placed by user")
        }
        if (!capturedInApp) {
            return if (exif != null) {
                LocationChoice(exif, "Imported photo: using EXIF GPS")
            } else {
                LocationChoice(null, "Imported photo has no EXIF GPS: drop a pin")
            }
        }
        val deviceAccuracy = device?.accuracyMeters
        if (deviceAccuracy != null && deviceAccuracy <= MAX_TRUSTED_DEVICE_ACCURACY_METERS) {
            val reason = "In-app photo: device fix within ${deviceAccuracy.toInt()} m"
            return LocationChoice(device, reason)
        }
        if (exif != null) {
            return LocationChoice(exif, "In-app photo: device fix missing or coarse, using EXIF")
        }
        if (device != null) {
            return LocationChoice(device, "In-app photo: only a coarse device fix is available")
        }
        return LocationChoice(null, "No location available: drop a pin")
    }

    /** Returns the great-circle distance between [a] and [b] in meters. */
    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }
}
