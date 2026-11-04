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

// The longest a Double prints, such as -1.2345678901234567E-300.
private const val MAX_COORDINATE_LENGTH = 24

// The characters around a pin's ID: its two quotes and the comma after it.
private const val ID_PUNCTUATION_LENGTH = 3

// A pin's two coordinates at their longest, with the comma between them.
private const val MAX_COORDINATES_LENGTH = 2 * MAX_COORDINATE_LENGTH + 1

// A pin's text at its longest apart from the ID's own characters, with the comma that separates it
// from the pin before.
private const val MAX_PIN_LENGTH_WITHOUT_ID = 1 + PIN_START.length + ID_START.length +
    ID_PUNCTUATION_LENGTH + COORDINATES_START.length + MAX_COORDINATES_LENGTH + PIN_END.length

// A control character is escaped as \u and its code in four hexadecimal digits.
private const val HEXADECIMAL = 16
private const val ESCAPE_DIGITS = 4

/**
 * Returns a GeoJSON FeatureCollection with a pin for each post of [posts] whose coordinates are
 * finite, in table order. Each pin's ID is its post's [PostTable.id], so tables holding the same
 * posts have the same pins, and a pin still identifies its post once the table has changed.
 */
internal fun pinsJsonOf(posts: PostTable): String {
    val json = pinsJsonBuilder(pinCount = posts.size, idLength = totalIdLength(posts))
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
    val json = pinsJsonBuilder(pinCount = 1, idLength = row?.let { posts.id(it).length } ?: 0)
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
    val json = pinsJsonBuilder(pinCount = 1, idLength = 0)
    if (point != null && isFinite(point.latitude, point.longitude)) {
        json.appendPinStart().appendPinCoordinates(point.latitude, point.longitude)
    }
    return json.append(COLLECTION_END).toString()
}

private fun totalIdLength(posts: PostTable): Int {
    var length = 0
    for (row in 0 until posts.size) {
        length += posts.id(row).length
    }
    return length
}

// Sized once for the pins' text, unless an ID needs escaping, which makes it grow.
private fun pinsJsonBuilder(pinCount: Int, idLength: Int): StringBuilder = StringBuilder(
    COLLECTION_START.length + pinCount * MAX_PIN_LENGTH_WITHOUT_ID + idLength +
        COLLECTION_END.length
).append(COLLECTION_START)

// Skips a post whose coordinates aren't finite: NaN and the infinities have no JSON spelling.
private fun StringBuilder.appendPin(posts: PostTable, row: Int) {
    val latitude = posts.latitude(row)
    val longitude = posts.longitude(row)
    if (isFinite(latitude, longitude)) {
        appendPinStart()
            .append(ID_START).appendJsonString(posts.id(row)).append(',')
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

// [text] as a JSON string: quoted, with its quotes, backslashes and control characters escaped.
private fun StringBuilder.appendJsonString(text: String): StringBuilder {
    append('"')
    for (char in text) {
        when {
            char == '"' || char == '\\' -> append('\\').append(char)
            char < ' ' -> appendUnicodeEscape(char)
            else -> append(char)
        }
    }
    return append('"')
}

private fun StringBuilder.appendUnicodeEscape(char: Char): StringBuilder =
    append("\\u").append(char.code.toString(HEXADECIMAL).padStart(ESCAPE_DIGITS, '0'))

// GeoJSON puts longitude first.
private fun StringBuilder.appendPinCoordinates(latitude: Double, longitude: Double): StringBuilder =
    append(COORDINATES_START).append(longitude).append(',').append(latitude).append(PIN_END)
