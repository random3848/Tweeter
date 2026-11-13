package edu.csuci.tweeter.data

/**
 * A user's profile picture, shown as their initials until users can upload a photo.
 *
 * @property initials One or two letters to show, such as "MK".
 */
data class Avatar(val initials: String)

/**
 * Returns the initials to show for [displayName]: the first letters of its first and last words,
 * uppercased, such as "MK" for "Maya Kim" and "M" for "Maya". Returns "?" when the name is blank.
 */
fun initialsOf(displayName: String): String {
    var first = NOT_FOUND
    var last = NOT_FOUND
    for (index in displayName.indices) {
        if (startsWord(displayName, index)) {
            if (first == NOT_FOUND) {
                first = index
            }
            last = index
        }
    }
    if (first == NOT_FOUND) {
        return "?"
    }

    val letters = StringBuilder(MAX_INITIALS_CHARS)
    letters.appendCodePoint(displayName.codePointAt(first))
    if (last != first) {
        letters.appendCodePoint(displayName.codePointAt(last))
    }
    return letters.toString().uppercase()
}

// Whether a word starts at [index]: its character isn't whitespace and the one before it is.
// The second half of a surrogate pair follows the first, which isn't whitespace either.
private fun startsWord(text: String, index: Int): Boolean =
    !text[index].isWhitespace() && (index == 0 || text[index - 1].isWhitespace())

private const val NOT_FOUND = -1

// Two characters outside the BMP take two chars each.
private const val MAX_INITIALS_CHARS = 4
