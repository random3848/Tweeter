package edu.csuci.tweeter.ui.search

import edu.csuci.tweeter.data.PostTable
import java.util.TreeMap

/**
 * An animal that posts name, as Search lists it.
 *
 * @property name The animal's name, as the newest post naming it spells it.
 * @property postCount How many posts name it.
 */
data class PostedAnimal(val name: String, val postCount: Int)

/**
 * Returns the items whose name, as [nameOf] gives it, contains [query], ignoring case and the
 * spaces around [query]: those starting with it first, then alphabetically. None for a blank
 * [query].
 */
internal fun <T> List<T>.filterByName(query: String, nameOf: (T) -> String): List<T> {
    val text = query.trim()
    if (text.isEmpty()) {
        return emptyList()
    }

    val startsWithQuery = compareByDescending<T> { nameOf(it).startsWith(text, ignoreCase = true) }
    return filter { nameOf(it).contains(text, ignoreCase = true) }
        .sortedWith(startsWithQuery.thenBy(String.CASE_INSENSITIVE_ORDER, nameOf))
}

/**
 * Returns the animals the posts of [posts] name ([PostTable.animalType]), alphabetically, each
 * once with how many posts name it. Names that differ only in case or in the spaces around them
 * are one animal, as [PostTable.rowsOfAnimal] finds its posts, spelt as the newest post spells it.
 * Posts naming no animal are left out. It's a pass over every post.
 */
internal fun postedAnimalsIn(posts: PostTable): List<PostedAnimal> {
    // Keeps the first spelling put in, which is the newest post's, since the table is newest first.
    val postCounts = TreeMap<String, Int>(String.CASE_INSENSITIVE_ORDER)
    for (row in 0 until posts.size) {
        val name = posts.animalType(row).trim()
        if (name.isNotEmpty()) {
            postCounts.merge(name, 1, Int::plus)
        }
    }
    return postCounts.map { (name, postCount) -> PostedAnimal(name, postCount) }
}
