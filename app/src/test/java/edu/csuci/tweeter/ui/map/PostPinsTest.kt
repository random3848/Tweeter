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

private const val FIRST_ROW = 0
private const val SECOND_ROW = 1

class PostPinsTest {
    private val campusTap = Position(longitude = TAP_LONGITUDE, latitude = CAMPUS_TAP_LATITUDE)

    @Test
    fun nearestPinnedRow_returnsRowClosestToTap() {
        val far = postAt(latitude = CAMPUS_TAP_LATITUDE + FAR_DEGREES, longitude = TAP_LONGITUDE)
        val near = postAt(latitude = CAMPUS_TAP_LATITUDE + NEAR_DEGREES, longitude = TAP_LONGITUDE)
        val posts = PostTable.of(listOf(far, near))

        assertEquals(SECOND_ROW, posts.nearestPinnedRow(pinsOf(posts), campusTap))
    }

    @Test
    fun nearestPinnedRow_countsDegreesOfLongitudeAsDrawnOnMap() {
        val north = postAt(
            latitude = FAR_NORTH_TAP_LATITUDE + SHORT_NORTH_DEGREES,
            longitude = TAP_LONGITUDE
        )
        val east = postAt(
            latitude = FAR_NORTH_TAP_LATITUDE,
            longitude = TAP_LONGITUDE + LONG_EAST_DEGREES
        )
        val posts = PostTable.of(listOf(north, east))
        val tap = Position(longitude = TAP_LONGITUDE, latitude = FAR_NORTH_TAP_LATITUDE)

        assertEquals(SECOND_ROW, posts.nearestPinnedRow(pinsOf(posts), tap))
    }

    @Test
    fun nearestPinnedRow_onlyAmongTappedPins() {
        val near = postAt(latitude = CAMPUS_TAP_LATITUDE + NEAR_DEGREES, longitude = TAP_LONGITUDE)
        val far = postAt(latitude = CAMPUS_TAP_LATITUDE + FAR_DEGREES, longitude = TAP_LONGITUDE)
        val posts = PostTable.of(listOf(near, far))
        val farPin = pinsOf(posts)[SECOND_ROW]

        assertEquals(SECOND_ROW, posts.nearestPinnedRow(listOf(farPin), campusTap))
    }

    @Test
    fun nearestPinnedRow_noPins_returnsNull() {
        val posts = PostTable.of(listOf(postAt(CAMPUS_TAP_LATITUDE, TAP_LONGITUDE)))

        assertNull(posts.nearestPinnedRow(emptyList(), campusTap))
    }

    @Test
    fun nearestPinnedRow_pinOfEarlierTable_returnsNull() {
        val post = postAt(CAMPUS_TAP_LATITUDE, TAP_LONGITUDE)
        val earlier = PostTable.of(listOf(post))
        val later = PostTable.of(listOf(post))

        assertNull(later.nearestPinnedRow(pinsOf(earlier), campusTap))
    }

    // MapLibre may keep a feature's numeric ID as a double, written back with a decimal point.
    @Test
    fun nearestPinnedRow_pinIdWrittenAsDouble_returnsItsRow() {
        val posts = PostTable.of(listOf(postAt(CAMPUS_TAP_LATITUDE, TAP_LONGITUDE)))
        val id = posts.refTo(FIRST_ROW).toLong()
        val pin = Feature.fromJson<Geometry?, JsonObject?>(
            """{"type":"Feature","id":$id.0,"geometry":null,"properties":null}"""
        )

        assertEquals(FIRST_ROW, posts.nearestPinnedRow(listOf(pin), campusTap))
    }

    private fun postAt(latitude: Double, longitude: Double): Post = SAMPLE_POSTS.first().copy(
        id = "$latitude,$longitude",
        latitude = latitude,
        longitude = longitude
    )

    // The pins as MapLibre hands them back: read from the GeoJSON the map draws.
    private fun pinsOf(posts: PostTable): List<Feature<Geometry, JsonObject?>> =
        FeatureCollection.fromJson<Geometry, JsonObject?>(pinsJsonOf(posts)).features
}
