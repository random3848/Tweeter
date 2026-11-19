package edu.csuci.tweeter.location

import edu.csuci.tweeter.data.NumberStore

// The keys of the last location's coordinates, each saved as the bits of its Double.
private const val LATITUDE_KEY = "lastLocation.latitudeBits"
private const val LONGITUDE_KEY = "lastLocation.longitudeBits"

/**
 * Where the device was when the app last got a location fix, kept in [numbers] so that checks
 * running while the app is closed know where the user is without asking for their location in
 * the background.
 */
class LastLocationStore(private val numbers: NumberStore) {
    /** Saves [point] as where the device last was, in place of what was saved before. */
    suspend fun save(point: GeoPoint) {
        numbers.putLongs(
            mapOf(
                LATITUDE_KEY to point.latitude.toRawBits(),
                LONGITUDE_KEY to point.longitude.toRawBits()
            )
        )
    }

    /** Where the device last was, or null if the app never got a fix. */
    suspend fun lastLocation(): GeoPoint? {
        val latitudeBits = numbers.getLong(LATITUDE_KEY) ?: return null
        val longitudeBits = numbers.getLong(LONGITUDE_KEY) ?: return null
        return GeoPoint(Double.fromBits(latitudeBits), Double.fromBits(longitudeBits))
    }
}
