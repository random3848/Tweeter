package edu.csuci.tweeter.data

import java.util.Arrays

// How a post without a confidence ([Post.confidence] null) is stored.
private const val NO_CONFIDENCE: Byte = 0

/**
 * Posts in one fixed order, newest first, stored as a structure of arrays: one array per field of
 * [Post] that a screen shows, each indexed by the post's row.
 *
 * Screens keep rows, not `Post` objects. A pass over one field, such as Map reading every post's
 * coordinates, reads only that field's array. A table never changes once built: changed posts come
 * in a new table, where [rowOf] finds a post again by its ID.
 *
 * Tables are equal only to themselves, so that UI state holding one compares it in constant time.
 * [PostRepository] emits a table only when its posts differ from the last one's, which
 * [holdsSamePosts] compares.
 *
 * @property size How many posts the table holds; their rows are 0 until [size]. The arrays can be
 *     longer, by the rows its [Builder] had room for, which the table never reads.
 */
class PostTable private constructor(
    val size: Int,
    private val ids: Array<String>,
    private val authorUids: Array<String>,
    private val usernames: Array<String>,
    private val avatarInitials: Array<String>,
    private val locationNames: Array<String>,
    private val latitudes: DoubleArray,
    private val longitudes: DoubleArray,
    private val imageUris: Array<String>,
    private val captions: Array<String>,
    private val likeCounts: IntArray,
    private val timestampsMillis: LongArray,
    private val animalTypes: Array<String>,
    private val confidences: ByteArray
) {
    /** The [Post.id] of the post in [row]. */
    fun id(row: Int): String = ids[row]

    /** The [Post.authorUid] of the post in [row]. */
    fun authorUid(row: Int): String = authorUids[row]

    /** The [Post.username] of the post in [row]. */
    fun username(row: Int): String = usernames[row]

    /** The initials of the [Post.avatar] of the post in [row]. */
    fun avatarInitials(row: Int): String = avatarInitials[row]

    /** The [Post.locationName] of the post in [row]. */
    fun locationName(row: Int): String = locationNames[row]

    /** The [Post.latitude] of the post in [row]. */
    fun latitude(row: Int): Double = latitudes[row]

    /** The [Post.longitude] of the post in [row]. */
    fun longitude(row: Int): Double = longitudes[row]

    /** The [Post.imageUri] of the post in [row]. */
    fun imageUri(row: Int): String = imageUris[row]

    /** The [Post.caption] of the post in [row]. */
    fun caption(row: Int): String = captions[row]

    /** The [Post.likeCount] of the post in [row]. */
    fun likeCount(row: Int): Int = likeCounts[row]

    /** The [Post.timestamp] of the post in [row], in milliseconds since the epoch. */
    fun timestampMillis(row: Int): Long = timestampsMillis[row]

    /** The [Post.animalType] of the post in [row]. */
    fun animalType(row: Int): String = animalTypes[row]

    /** The [Post.confidence] of the post in [row]. */
    fun confidence(row: Int): Int? = confidences[row].takeIf { it != NO_CONFIDENCE }?.toInt()

    /** The row of the post with [postId], or null if the table doesn't hold it. */
    fun rowOf(postId: String): Int? {
        for (row in 0 until size) {
            if (ids[row] == postId) {
                return row
            }
        }
        return null
    }

    /** The rows of the posts shared by user [authorUid] ([Post.authorUid]), in table order. */
    fun rowsBy(authorUid: String): IntArray = rowsWhere(authorUids) { it == authorUid }

    /** The rows of the posts shared by any of the users [authorUids], in table order. */
    fun rowsBy(authorUids: Set<String>): IntArray = rowsWhere(this.authorUids, authorUids::contains)

    /**
     * The rows of the posts of [animal] ([Post.animalType]), in table order: the posts naming it
     * the same way, ignoring case and the spaces around it. A blank [animal] matches no post.
     */
    fun rowsOfAnimal(animal: String): IntArray {
        val name = animal.trim()
        if (name.isEmpty()) {
            return IntArray(0)
        }
        return rowsWhere(animalTypes) { it.trim().equals(name, ignoreCase = true) }
    }

    /**
     * Every row, the most liked post ([Post.likeCount]) first; posts liked equally keep their
     * table order, newest first. It sorts the whole table, so it's a pass over every post.
     */
    fun rowsByLikeCount(): IntArray = rowsByDescending { row -> likeCounts[row].toDouble() }

    /**
     * Every row, the post with the highest [score] first; posts that score the same keep their
     * table order, newest first. It sorts the whole table, so it's a pass over every post, and
     * calls [score] once per row.
     */
    fun rowsByDescending(score: (row: Int) -> Double): IntArray {
        val scores = DoubleArray(size) { row -> score(row) }
        return (0 until size).sortedByDescending { row -> scores[row] }.toIntArray()
    }

    /**
     * Whether [other] holds the same posts as this table, in the same rows. It compares every
     * shown field of every post, so it's a pass over the whole table.
     */
    fun holdsSamePosts(other: PostTable): Boolean =
        this === other || (size == other.size && hasSameTexts(other) && hasSameNumbers(other))

    override fun toString(): String = "PostTable(ids=${ids.asList().subList(0, size)})"

    // The rows whose value in [column] is wanted, in table order. Counts them first, so they go
    // straight into an array of their size.
    private inline fun rowsWhere(column: Array<String>, isWanted: (String) -> Boolean): IntArray {
        val rows = IntArray(countRowsWhere(column, isWanted))
        var found = 0
        for (row in 0 until size) {
            if (isWanted(column[row])) {
                rows[found] = row
                found++
            }
        }
        return rows
    }

    private inline fun countRowsWhere(column: Array<String>, isWanted: (String) -> Boolean): Int {
        var count = 0
        for (row in 0 until size) {
            if (isWanted(column[row])) {
                count++
            }
        }
        return count
    }

    private fun hasSameTexts(other: PostTable): Boolean =
        haveSameRows(ids, other.ids) && haveSameRows(authorUids, other.authorUids) &&
            haveSameRows(usernames, other.usernames) &&
            haveSameRows(avatarInitials, other.avatarInitials) &&
            haveSameRows(locationNames, other.locationNames) &&
            haveSameRows(imageUris, other.imageUris) && haveSameRows(captions, other.captions) &&
            haveSameRows(animalTypes, other.animalTypes)

    private fun hasSameNumbers(other: PostTable): Boolean =
        haveSameRows(latitudes, other.latitudes) && haveSameRows(longitudes, other.longitudes) &&
            haveSameRows(likeCounts, other.likeCounts) &&
            haveSameRows(timestampsMillis, other.timestampsMillis) &&
            haveSameRows(confidences, other.confidences)

    // Each compares the rows 0 until size alone, past which a column can hold anything.
    private fun haveSameRows(column: Array<String>, otherColumn: Array<String>): Boolean =
        Arrays.equals(column, 0, size, otherColumn, 0, size)

    private fun haveSameRows(column: DoubleArray, otherColumn: DoubleArray): Boolean =
        Arrays.equals(column, 0, size, otherColumn, 0, size)

    private fun haveSameRows(column: IntArray, otherColumn: IntArray): Boolean =
        Arrays.equals(column, 0, size, otherColumn, 0, size)

    private fun haveSameRows(column: LongArray, otherColumn: LongArray): Boolean =
        Arrays.equals(column, 0, size, otherColumn, 0, size)

    private fun haveSameRows(column: ByteArray, otherColumn: ByteArray): Boolean =
        Arrays.equals(column, 0, size, otherColumn, 0, size)

    /**
     * Puts posts, in the order they are added, into a [PostTable].
     *
     * @param capacity How many posts it can take. The arrays are allocated once, at this size.
     */
    class Builder(private val capacity: Int) {
        private var size = 0
        private val ids = Array(capacity) { "" }
        private val authorUids = Array(capacity) { "" }
        private val usernames = Array(capacity) { "" }
        private val avatarInitials = Array(capacity) { "" }
        private val locationNames = Array(capacity) { "" }
        private val latitudes = DoubleArray(capacity)
        private val longitudes = DoubleArray(capacity)
        private val imageUris = Array(capacity) { "" }
        private val captions = Array(capacity) { "" }
        private val likeCounts = IntArray(capacity)
        private val timestampsMillis = LongArray(capacity)
        private val animalTypes = Array(capacity) { "" }
        private val confidences = ByteArray(capacity)

        /**
         * Adds [post] in the next row.
         *
         * @throws IllegalStateException If the builder already holds [capacity] posts.
         */
        fun add(post: Post) {
            check(size < capacity) { "Can't add a post past the capacity of $capacity" }
            addTexts(size, post)
            addNumbers(size, post)
            size++
        }

        /**
         * Returns the table of the posts added so far. It shares the builder's arrays without
         * copying them: a post added later goes into a row past the table's size.
         */
        fun build(): PostTable = PostTable(
            size = size,
            ids = ids,
            authorUids = authorUids,
            usernames = usernames,
            avatarInitials = avatarInitials,
            locationNames = locationNames,
            latitudes = latitudes,
            longitudes = longitudes,
            imageUris = imageUris,
            captions = captions,
            likeCounts = likeCounts,
            timestampsMillis = timestampsMillis,
            animalTypes = animalTypes,
            confidences = confidences
        )

        private fun addTexts(row: Int, post: Post) {
            ids[row] = post.id
            authorUids[row] = post.authorUid
            usernames[row] = post.username
            avatarInitials[row] = post.avatar.initials
            locationNames[row] = post.locationName
            imageUris[row] = post.imageUri
            captions[row] = post.caption
            animalTypes[row] = post.animalType
        }

        private fun addNumbers(row: Int, post: Post) {
            latitudes[row] = post.latitude
            longitudes[row] = post.longitude
            likeCounts[row] = post.likeCount
            timestampsMillis[row] = post.timestamp.toEpochMilli()
            // Post keeps confidences from 1 to 5, which a Byte holds, and 0 means none.
            confidences[row] = post.confidence?.toByte() ?: NO_CONFIDENCE
        }
    }

    companion object {
        /** A table of no posts. */
        val EMPTY: PostTable = Builder(capacity = 0).build()

        /** Returns the table of [posts], in their order. */
        fun of(posts: List<Post>): PostTable {
            val builder = Builder(capacity = posts.size)
            for (post in posts) {
                builder.add(post)
            }
            return builder.build()
        }
    }
}
