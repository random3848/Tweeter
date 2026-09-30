package edu.csuci.tweeter.location

/**
 * A latitude/longitude pair in decimal degrees (WGS84).
 *
 * @property latitude degrees north, -90 to 90.
 * @property longitude degrees east, -180 to 180.
 */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Where a location fix came from. */
enum class LocationSource {
    /** GPS tags written into the photo's EXIF metadata by the camera app. */
    EXIF,

    /** A live fix from the device's location services. */
    DEVICE,

    /** A pin the user dropped on the map by hand. */
    MANUAL,
}

/**
 * A single location estimate for a photo.
 *
 * @property point the estimated position.
 * @property source where the estimate came from.
 * @property accuracyMeters the reported 68% confidence radius, or null if the source gives none.
 * @property timeMillis when the fix was taken (epoch millis), or null if unknown.
 */
data class LocationFix(
    val point: GeoPoint,
    val source: LocationSource,
    val accuracyMeters: Float? = null,
    val timeMillis: Long? = null,
)
