package edu.csuci.tweeter.location

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// The Earth's mean radius. Treating the Earth as a sphere of it is off by under 0.5%.
private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * The distance from this point to [other] along the Earth's surface, in meters, by the haversine
 * formula, which stays accurate for points close together and takes the short way across the
 * antimeridian.
 */
fun GeoPoint.distanceMetersTo(other: GeoPoint): Double {
    val latitudeRadians = Math.toRadians(latitude)
    val otherLatitudeRadians = Math.toRadians(other.latitude)
    val halfLatitudeChange = (otherLatitudeRadians - latitudeRadians) / 2
    val halfLongitudeChange = Math.toRadians(other.longitude - longitude) / 2
    val haversine = sin(halfLatitudeChange) * sin(halfLatitudeChange) +
        cos(latitudeRadians) * cos(otherLatitudeRadians) *
        sin(halfLongitudeChange) * sin(halfLongitudeChange)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine))
}
