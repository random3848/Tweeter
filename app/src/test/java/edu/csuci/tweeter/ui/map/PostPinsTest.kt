package edu.csuci.tweeter.ui.map

import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Point
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

private const val SECOND_PIN = 1

class PostPinsTest {
    private val campusTap = Position(longitude = TAP_LONGITUDE, latitude = CAMPUS_TAP_LATITUDE)

    @Test
    fun nearestPinnedPostId_returnsPostOfPinClosestToTap() {
        val far = postAt(latitude = CAMPUS_TAP_LATITUDE + FAR_DEGREES, longitude = TAP_LONGITUDE)
        val near = postAt(latitude = CAMPUS_TAP_LATITUDE + NEAR_DEGREES, longitude = TAP_LONGITUDE)

        assertEquals(near.id, nearestPinnedPostId(pinsOf(far, near), campusTap))
    }

    @Test
    fun nearestPinnedPostId_countsDegreesOfLongitudeAsDrawnOnMap() {
        val north = postAt(
            latitude = FAR_NORTH_TAP_LATITUDE + SHORT_NORTH_DEGREES,
            longitude = TAP_LONGITUDE
        )
        val east = postAt(
            latitude = FAR_NORTH_TAP_LATITUDE,
            longitude = TAP_LONGITUDE + LONG_EAST_DEGREES
        )
        val tap = Position(longitude = TAP_LONGITUDE, latitude = FAR_NORTH_TAP_LATITUDE)

        assertEquals(east.id, nearestPinnedPostId(pinsOf(north, east), tap))
    }

    @Test
    fun nearestPinnedPostId_onlyAmongTappedPins() {
        val near = postAt(latitude = CAMPUS_TAP_LATITUDE + NEAR_DEGREES, longitude = TAP_LONGITUDE)
        val far = postAt(latitude = CAMPUS_TAP_LATITUDE + FAR_DEGREES, longitude = TAP_LONGITUDE)
        val farPin = pinsOf(near, far)[SECOND_PIN]

        assertEquals(far.id, nearestPinnedPostId(listOf(farPin), campusTap))
    }

    @Test
    fun nearestPinnedPostId_noPins_returnsNull() {
        assertNull(nearestPinnedPostId(emptyList(), campusTap))
    }

    @Test
    fun nearestPinnedPostId_featureWithoutId_isNoPin() {
        val unnamed = Feature(geometry = Point(campusTap), properties = null)

        assertNull(nearestPinnedPostId(listOf(unnamed), campusTap))
    }

    private fun postAt(latitude: Double, longitude: Double): Post = SAMPLE_POSTS.first().copy(
        id = "$latitude,$longitude",
        latitude = latitude,
        longitude = longitude
    )

    // The pins of [posts] as MapLibre hands them back: read from the GeoJSON the map draws.
    private fun pinsOf(vararg posts: Post): List<Feature<Geometry, JsonObject?>> =
        FeatureCollection.fromJson<Geometry, JsonObject?>(pinsJsonOf(PostTable.of(posts.asList())))
            .features
}
