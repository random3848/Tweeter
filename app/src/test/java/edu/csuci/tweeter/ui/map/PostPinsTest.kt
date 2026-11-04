package edu.csuci.tweeter.ui.map

import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.SAMPLE_POSTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.spatialk.geojson.Position

private const val TAP_LONGITUDE = -119.0
private const val CAMPUS_TAP_LATITUDE = 34.0

// At 60 degrees north, a degree of longitude is drawn half as long as a degree of latitude.
private const val FAR_NORTH_TAP_LATITUDE = 60.0

private const val NEAR_DEGREES = 0.0001
private const val FAR_DEGREES = 0.001

// Drawn 0.0005 degrees of latitude away at 60 degrees north: nearer than SHORT_NORTH_DEGREES.
private const val LONG_EAST_DEGREES = 0.001
private const val SHORT_NORTH_DEGREES = 0.0007

class PostPinsTest {
    @Test
    fun nearestTo_returnsPostClosestToTap() {
        val near = postAt(latitude = CAMPUS_TAP_LATITUDE + NEAR_DEGREES, longitude = TAP_LONGITUDE)
        val far = postAt(latitude = CAMPUS_TAP_LATITUDE + FAR_DEGREES, longitude = TAP_LONGITUDE)
        val tap = Position(longitude = TAP_LONGITUDE, latitude = CAMPUS_TAP_LATITUDE)

        assertEquals(near, listOf(far, near).nearestTo(tap))
    }

    @Test
    fun nearestTo_countsDegreesOfLongitudeAsDrawnOnMap() {
        val east = postAt(
            latitude = FAR_NORTH_TAP_LATITUDE,
            longitude = TAP_LONGITUDE + LONG_EAST_DEGREES
        )
        val north = postAt(
            latitude = FAR_NORTH_TAP_LATITUDE + SHORT_NORTH_DEGREES,
            longitude = TAP_LONGITUDE
        )
        val tap = Position(longitude = TAP_LONGITUDE, latitude = FAR_NORTH_TAP_LATITUDE)

        assertEquals(east, listOf(north, east).nearestTo(tap))
    }

    @Test
    fun nearestTo_noPosts_returnsNull() {
        val tap = Position(longitude = TAP_LONGITUDE, latitude = CAMPUS_TAP_LATITUDE)

        assertNull(emptyList<Post>().nearestTo(tap))
    }

    private fun postAt(latitude: Double, longitude: Double): Post = SAMPLE_POSTS.first().copy(
        id = "$latitude,$longitude",
        latitude = latitude,
        longitude = longitude
    )
}
