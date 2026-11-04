package edu.csuci.tweeter.ui.map

import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.location.GeoPoint

// A pin is a GeoJSON Point feature, written straight from its coordinates into one string: no
// feature objects per pin, and no serializer walking them.
private const val COLLECTION_START = """{"type":"FeatureCollection","features":["""
private const val COLLECTION_END = "]}"
private const val PIN_START = """{"type":"Feature","""
private const val ID_START = """"id":"""
private const val COORDINATES_START = """"geometry":{"type":"Point","coordinates":["""
private const val PIN_END = """]},"properties":null}"""

// The longest a Double or a PostRef prints, such as -1.2345678901234567E-300.
private const val MAX_NUMBER_LENGTH = 24

// The numbers in a pin: its ID and two coordinates.
private const val NUMBERS_PER_PIN = 3

// A pin's text at its longest, with a comma for each number: after the ID, between the
// coordinates, and before the pin to separate it from the one before.
private const val MAX_PIN_LENGTH = PIN_START.length + ID_START.length + COORDINATES_START.length +
    PIN_END.length + NUMBERS_PER_PIN * (MAX_NUMBER_LENGTH + 1)

/**
 * Returns a GeoJSON FeatureCollection with a pin for each post of [posts] whose coordinates are
 * finite, in table order. Each pin's numeric ID is the [edu.csuci.tweeter.data.PostRef] of its
 * post's row, as [edu.csuci.tweeter.data.PostRef.toLong] writes it.
 */
internal fun pinsJsonOf(posts: PostTable): String {
    val json = pinsJsonBuilder(maxPins = posts.size)
    for (row in 0 until posts.size) {
        json.appendPin(posts, row)
    }
    return json.append(COLLECTION_END).toString()
}

/**
 * Returns a GeoJSON FeatureCollection with the pin of [row] of [posts] alone, written as
 * [pinsJsonOf] writes it, or with no pin if [row] is null.
 */
internal fun pinJsonOf(posts: PostTable, row: Int?): String {
    val json = pinsJsonBuilder(maxPins = 1)
    if (row != null) {
        json.appendPin(posts, row)
    }
    return json.append(COLLECTION_END).toString()
}

/**
 * Returns a GeoJSON FeatureCollection with one pin, without an ID, at [point], or with no pin if
 * [point] is null.
 */
internal fun pinJsonAt(point: GeoPoint?): String {
    val json = pinsJsonBuilder(maxPins = 1)
    if (point != null && isFinite(point.latitude, point.longitude)) {
        json.appendPinStart().appendPinCoordinates(point.latitude, point.longitude)
    }
    return json.append(COLLECTION_END).toString()
}

// Sized once, for the longest text the pins can take.
private fun pinsJsonBuilder(maxPins: Int): StringBuilder =
    StringBuilder(COLLECTION_START.length + maxPins * MAX_PIN_LENGTH + COLLECTION_END.length)
        .append(COLLECTION_START)

// Skips a post whose coordinates aren't finite: NaN and the infinities have no JSON spelling.
private fun StringBuilder.appendPin(posts: PostTable, row: Int) {
    val latitude = posts.latitude(row)
    val longitude = posts.longitude(row)
    if (isFinite(latitude, longitude)) {
        appendPinStart()
            .append(ID_START).append(posts.refTo(row).toLong()).append(',')
            .appendPinCoordinates(latitude, longitude)
    }
}

private fun isFinite(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite()

private fun StringBuilder.appendPinStart(): StringBuilder {
    val isFirstPin = length == COLLECTION_START.length
    if (!isFirstPin) {
        append(',')
    }
    return append(PIN_START)
}

// GeoJSON puts longitude first.
private fun StringBuilder.appendPinCoordinates(latitude: Double, longitude: Double): StringBuilder =
    append(COORDINATES_START).append(longitude).append(',').append(latitude).append(PIN_END)
