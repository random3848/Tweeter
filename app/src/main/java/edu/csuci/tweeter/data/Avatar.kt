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
    val words = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) {
        return "?"
    }

    val letters = if (words.size ==
        1
    ) {
        listOf(words.first())
    } else {
        listOf(words.first(), words.last())
    }
    return letters.joinToString("") { firstLetterOf(it).uppercase() }
}

// The first character, keeping both halves of a character outside the BMP, such as an emoji.
private fun firstLetterOf(word: String): String = word.substring(0, word.offsetByCodePoints(0, 1))
