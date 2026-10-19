package edu.csuci.tweeter.location

/**
 * A latitude/longitude pair in decimal degrees (WGS84).
 *
 * @property latitude Degrees north, -90 to 90.
 * @property longitude Degrees east, -180 to 180.
 */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Where a [LocationFix] came from. */
enum class LocationSource {
    /** GPS tags the camera app wrote into the photo's EXIF metadata. */
    EXIF,

    /** A live fix from the device's location services. */
    DEVICE,

    /** A pin the user placed on the map. */
    MANUAL
}

/**
 * One estimate of where a photo was taken.
 *
 * @property point The estimated position.
 * @property source Where the estimate came from.
 * @property accuracyMeters The 68% confidence radius, or null if the source gives none.
 * @property timeMillis When the fix was taken (epoch milliseconds), or null if unknown.
 */
data class LocationFix(
    val point: GeoPoint,
    val source: LocationSource,
    val accuracyMeters: Float? = null,
    val timeMillis: Long? = null
)
