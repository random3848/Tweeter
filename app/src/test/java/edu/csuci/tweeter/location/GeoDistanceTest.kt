package edu.csuci.tweeter.location

import org.junit.Assert.assertEquals
import org.junit.Test

// The length of one degree along a great circle of the sphere the distances are measured on.
private const val METERS_PER_DEGREE = 111_194.93
private const val TOLERANCE_METERS = 1.0

private val CAMPUS = GeoPoint(latitude = 34.1626, longitude = -119.0434)
private val LOS_ANGELES_CITY_HALL = GeoPoint(latitude = 34.0537, longitude = -118.2428)
private const val CAMPUS_TO_CITY_HALL_METERS = 74_696.86

class GeoDistanceTest {
    @Test
    fun distanceMetersTo_samePoint_isZero() {
        assertEquals(0.0, CAMPUS.distanceMetersTo(CAMPUS), TOLERANCE_METERS)
    }

    @Test
    fun distanceMetersTo_oneDegreeNorth_isOneDegreeOfArc() {
        val equator = GeoPoint(latitude = 0.0, longitude = 0.0)
        val north = GeoPoint(latitude = 1.0, longitude = 0.0)

        assertEquals(METERS_PER_DEGREE, equator.distanceMetersTo(north), TOLERANCE_METERS)
    }

    @Test
    fun distanceMetersTo_acrossAntimeridian_takesShortWay() {
        val west = GeoPoint(latitude = 0.0, longitude = 179.5)
        val east = GeoPoint(latitude = 0.0, longitude = -179.5)

        assertEquals(METERS_PER_DEGREE, west.distanceMetersTo(east), TOLERANCE_METERS)
    }

    @Test
    fun distanceMetersTo_campusToCityHall_isGreatCircleDistance() {
        val distance = CAMPUS.distanceMetersTo(LOS_ANGELES_CITY_HALL)

        assertEquals(CAMPUS_TO_CITY_HALL_METERS, distance, TOLERANCE_METERS)
    }

    @Test
    fun distanceMetersTo_isSameBothWays() {
        assertEquals(
            CAMPUS.distanceMetersTo(LOS_ANGELES_CITY_HALL),
            LOS_ANGELES_CITY_HALL.distanceMetersTo(CAMPUS),
            TOLERANCE_METERS
        )
    }
}
