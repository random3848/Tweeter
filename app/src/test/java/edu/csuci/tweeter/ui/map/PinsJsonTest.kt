package edu.csuci.tweeter.ui.map

import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import edu.csuci.tweeter.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

// Coordinates that print as written, so that the expected JSON can spell them.
private const val CAMPUS_LATITUDE_TEXT = "34.5"
private const val CAMPUS_LONGITUDE_TEXT = "-119.25"
private const val ISLAND_LATITUDE_TEXT = "33.75"
private const val ISLAND_LONGITUDE_TEXT = "-120.0"

private const val SECOND_ROW = 1

private const val EMPTY_COLLECTION = """{"type":"FeatureCollection","features":[]}"""

class PinsJsonTest {
    private val campus = postAt(
        id = "campus",
        latitude = CAMPUS_LATITUDE_TEXT.toDouble(),
        longitude = CAMPUS_LONGITUDE_TEXT.toDouble()
    )
    private val island = postAt(
        id = "island",
        latitude = ISLAND_LATITUDE_TEXT.toDouble(),
        longitude = ISLAND_LONGITUDE_TEXT.toDouble()
    )

    @Test
    fun pinsJsonOf_emptyTable_writesEmptyCollection() {
        assertEquals(EMPTY_COLLECTION, pinsJsonOf(PostTable.EMPTY))
    }

    @Test
    fun pinsJsonOf_onePost_writesPinAtLongitudeThenLatitudeWithPostIdAsId() {
        val posts = PostTable.of(listOf(campus))

        val json = pinsJsonOf(posts)

        assertEquals(
            """{"type":"FeatureCollection","features":[""" +
                """{"type":"Feature","id":"campus","geometry":{"type":"Point",""" +
                """"coordinates":[$CAMPUS_LONGITUDE_TEXT,$CAMPUS_LATITUDE_TEXT]},""" +
                """"properties":null}]}""",
            json
        )
    }

    @Test
    fun pinsJsonOf_twoPosts_writesPinsInTableOrder() {
        val posts = PostTable.of(listOf(campus, island))

        val json = pinsJsonOf(posts)

        assertEquals(
            """{"type":"FeatureCollection","features":[""" +
                """{"type":"Feature","id":"campus","geometry":{"type":"Point",""" +
                """"coordinates":[$CAMPUS_LONGITUDE_TEXT,$CAMPUS_LATITUDE_TEXT]},""" +
                """"properties":null},""" +
                """{"type":"Feature","id":"island","geometry":{"type":"Point",""" +
                """"coordinates":[$ISLAND_LONGITUDE_TEXT,$ISLAND_LATITUDE_TEXT]},""" +
                """"properties":null}]}""",
            json
        )
    }

    @Test
    fun pinsJsonOf_idWithQuoteBackslashAndControlCharacter_escapesThem() {
        val posts = PostTable.of(listOf(campus.copy(id = "a\"b\\c\u0001")))

        val json = pinsJsonOf(posts)

        assertEquals(
            """{"type":"FeatureCollection","features":[""" +
                """{"type":"Feature","id":"a\"b\\c\u0001","geometry":{"type":"Point",""" +
                """"coordinates":[$CAMPUS_LONGITUDE_TEXT,$CAMPUS_LATITUDE_TEXT]},""" +
                """"properties":null}]}""",
            json
        )
    }

    @Test
    fun pinsJsonOf_coordinatesNotFinite_skipsThosePosts() {
        val unknownLatitude = campus.copy(id = "unknown", latitude = Double.NaN)
        val infiniteLongitude = campus.copy(id = "infinite", longitude = Double.POSITIVE_INFINITY)
        val posts = PostTable.of(listOf(unknownLatitude, infiniteLongitude, island))

        val json = pinsJsonOf(posts)

        assertEquals(
            """{"type":"FeatureCollection","features":[""" +
                """{"type":"Feature","id":"island","geometry":{"type":"Point",""" +
                """"coordinates":[$ISLAND_LONGITUDE_TEXT,$ISLAND_LATITUDE_TEXT]},""" +
                """"properties":null}]}""",
            json
        )
    }

    @Test
    fun pinJsonOf_row_writesThatPinAlone() {
        val posts = PostTable.of(listOf(campus, island))

        val json = pinJsonOf(posts, SECOND_ROW)

        assertEquals(
            """{"type":"FeatureCollection","features":[""" +
                """{"type":"Feature","id":"island","geometry":{"type":"Point",""" +
                """"coordinates":[$ISLAND_LONGITUDE_TEXT,$ISLAND_LATITUDE_TEXT]},""" +
                """"properties":null}]}""",
            json
        )
    }

    @Test
    fun pinJsonOf_noRow_writesEmptyCollection() {
        val posts = PostTable.of(listOf(campus))

        val json = pinJsonOf(posts, row = null)

        assertEquals(EMPTY_COLLECTION, json)
    }

    @Test
    fun pinJsonAt_point_writesPinWithoutId() {
        val point = GeoPoint(campus.latitude, campus.longitude)

        val json = pinJsonAt(point)

        assertEquals(
            """{"type":"FeatureCollection","features":[""" +
                """{"type":"Feature","geometry":{"type":"Point",""" +
                """"coordinates":[$CAMPUS_LONGITUDE_TEXT,$CAMPUS_LATITUDE_TEXT]},""" +
                """"properties":null}]}""",
            json
        )
    }

    @Test
    fun pinJsonAt_noPoint_writesEmptyCollection() {
        assertEquals(EMPTY_COLLECTION, pinJsonAt(point = null))
    }

    private fun postAt(id: String, latitude: Double, longitude: Double): Post =
        SAMPLE_POSTS.first().copy(id = id, latitude = latitude, longitude = longitude)
}
