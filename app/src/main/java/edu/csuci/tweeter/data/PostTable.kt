package edu.csuci.tweeter.data

import java.util.BitSet

// How a post without a confidence ([Post.confidence] null) is stored.
private const val NO_CONFIDENCE: Byte = 0

/**
 * Posts in one fixed order, newest first, stored as a structure of arrays: one array per field of
 * [Post], each indexed by the post's row.
 *
 * Screens keep rows, not `Post` objects. A pass over one field, such as Map reading every post's
 * coordinates, reads only that field's array, and the two Boolean fields are bitsets, one bit per
 * post. A table never changes once built: changed posts come in a new table, where [rowOf] finds a
 * post again by its ID.
 */
class PostTable private constructor(
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
    private val commentCounts: IntArray,
    private val liked: BitSet,
    private val bookmarked: BitSet,
    private val timestampsMillis: LongArray,
    private val animalTypes: Array<String>,
    private val confidences: ByteArray
) {
    /** How many posts the table holds; their rows are 0 until [size]. */
    val size: Int
        get() = ids.size

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

    /** The [Post.commentCount] of the post in [row]. */
    fun commentCount(row: Int): Int = commentCounts[row]

    /** The [Post.isLiked] of the post in [row]. */
    fun isLiked(row: Int): Boolean = liked[row]

    /** The [Post.isBookmarked] of the post in [row]. */
    fun isBookmarked(row: Int): Boolean = bookmarked[row]

    /** The [Post.timestamp] of the post in [row], in milliseconds since the epoch. */
    fun timestampMillis(row: Int): Long = timestampsMillis[row]

    /** The [Post.animalType] of the post in [row]. */
    fun animalType(row: Int): String = animalTypes[row]

    /** The [Post.confidence] of the post in [row]. */
    fun confidence(row: Int): Int? = confidences[row].takeIf { it != NO_CONFIDENCE }?.toInt()

    /** The row of the post with [postId], or null if the table doesn't hold it. */
    fun rowOf(postId: String): Int? = ids.indexOf(postId).takeIf { it >= 0 }

    /** The rows of the posts shared by user [authorUid] ([Post.authorUid]), in table order. */
    fun rowsBy(authorUid: String): IntArray {
        val rows = IntArray(authorUids.count { it == authorUid })
        var found = 0
        for (row in authorUids.indices) {
            if (authorUids[row] == authorUid) {
                rows[found] = row
                found++
            }
        }
        return rows
    }

    override fun equals(other: Any?): Boolean =
        other is PostTable && columns().contentDeepEquals(other.columns())

    override fun hashCode(): Int = columns().contentDeepHashCode()

    override fun toString(): String = "PostTable(ids=${ids.contentToString()})"

    // Every column, so that tables holding the same posts are equal.
    private fun columns(): Array<Any> = arrayOf(
        ids, authorUids, usernames, avatarInitials, locationNames, latitudes, longitudes, imageUris,
        captions, likeCounts, commentCounts, liked, bookmarked, timestampsMillis, animalTypes,
        confidences
    )

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
        private val commentCounts = IntArray(capacity)
        private val liked = BitSet(capacity)
        private val bookmarked = BitSet(capacity)
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
            addFlags(size, post)
            size++
        }

        /** Returns the table of the posts added so far, sharing the arrays when they are full. */
        fun build(): PostTable = PostTable(
            ids = ids.trimmedTo(size),
            authorUids = authorUids.trimmedTo(size),
            usernames = usernames.trimmedTo(size),
            avatarInitials = avatarInitials.trimmedTo(size),
            locationNames = locationNames.trimmedTo(size),
            latitudes = latitudes.trimmedTo(size),
            longitudes = longitudes.trimmedTo(size),
            imageUris = imageUris.trimmedTo(size),
            captions = captions.trimmedTo(size),
            likeCounts = likeCounts.trimmedTo(size),
            commentCounts = commentCounts.trimmedTo(size),
            liked = liked.trimmedTo(size),
            bookmarked = bookmarked.trimmedTo(size),
            timestampsMillis = timestampsMillis.trimmedTo(size),
            animalTypes = animalTypes.trimmedTo(size),
            confidences = confidences.trimmedTo(size)
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
            commentCounts[row] = post.commentCount
            timestampsMillis[row] = post.timestamp.toEpochMilli()
            confidences[row] = post.confidence?.toConfidenceByte() ?: NO_CONFIDENCE
        }

        private fun addFlags(row: Int, post: Post) {
            liked[row] = post.isLiked
            bookmarked[row] = post.isBookmarked
        }

        // A bitset shared with the table while rows can still be added would change the table.
        private fun BitSet.trimmedTo(size: Int): BitSet = if (size ==
            capacity
        ) {
            this
        } else {
            get(0, size)
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

private fun Int.toConfidenceByte(): Byte {
    require(this in 1..Byte.MAX_VALUE) { "Confidence $this can't be stored" }
    return toByte()
}

private fun Array<String>.trimmedTo(size: Int): Array<String> =
    if (size == this.size) this else copyOfRange(0, size)

private fun DoubleArray.trimmedTo(size: Int): DoubleArray =
    if (size == this.size) this else copyOf(size)

private fun IntArray.trimmedTo(size: Int): IntArray = if (size == this.size) this else copyOf(size)

private fun LongArray.trimmedTo(size: Int): LongArray = if (size ==
    this.size
) {
    this
} else {
    copyOf(size)
}

private fun ByteArray.trimmedTo(size: Int): ByteArray = if (size ==
    this.size
) {
    this
} else {
    copyOf(size)
}
