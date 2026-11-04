package edu.csuci.tweeter.data

import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val UNKNOWN_ID = "no-such-post"
private const val MAYA = "maya"
private const val JORDAN = "jordan"

// A bitset keeps 64 rows in each word, so rows 63 and 64 sit on either side of a word boundary.
private const val ROWS_ACROSS_TWO_WORDS = 66
private const val LAST_ROW_OF_FIRST_WORD = 63
private const val FIRST_ROW_OF_SECOND_WORD = 64

private const val MOST_CONFIDENT = 5

class PostTableTest {
    // The table keeps a post's time to the millisecond.
    private val posts = SAMPLE_POSTS.map { it.copy(timestamp = it.timestamp.truncatedTo(MILLIS)) }

    @Test
    fun of_posts_eachRowHoldsItsPostsFields() {
        val table = PostTable.of(posts)

        assertEquals(posts, posts.indices.map { table.postAt(it) })
    }

    @Test
    fun timestampMillis_isPostsTimeToTheMillisecond() {
        val post = SAMPLE_POSTS.first().copy(timestamp = Instant.ofEpochSecond(1, 999_999))

        assertEquals(1_000L, PostTable.of(listOf(post)).timestampMillis(0))
    }

    @Test
    fun of_noPosts_isEmpty() {
        assertEquals(0, PostTable.of(emptyList()).size)
    }

    @Test
    fun confidence_unspecified_isNull() {
        val table = PostTable.of(listOf(SAMPLE_POSTS.first().copy(confidence = null)))

        assertNull(table.confidence(0))
    }

    @Test
    fun confidence_specified_isKept() {
        val table = PostTable.of(listOf(SAMPLE_POSTS.first().copy(confidence = MOST_CONFIDENT)))

        assertEquals(MOST_CONFIDENT, table.confidence(0))
    }

    @Test
    fun isLiked_rowsOnEitherSideOfBitsetWordBoundary_keepTheirOwnFlags() {
        val posts = List(ROWS_ACROSS_TWO_WORDS) { row ->
            SAMPLE_POSTS.first().copy(id = "$row", isLiked = row == FIRST_ROW_OF_SECOND_WORD)
        }

        val table = PostTable.of(posts)

        assertFalse(table.isLiked(LAST_ROW_OF_FIRST_WORD))
        assertTrue(table.isLiked(FIRST_ROW_OF_SECOND_WORD))
    }

    @Test
    fun isBookmarked_isKeptApartFromIsLiked() {
        val post = SAMPLE_POSTS.first().copy(isLiked = true, isBookmarked = false)

        assertFalse(PostTable.of(listOf(post)).isBookmarked(0))
    }

    @Test
    fun rowOf_postId_returnsItsRow() {
        val table = PostTable.of(SAMPLE_POSTS)

        assertEquals(2, table.rowOf(SAMPLE_POSTS[2].id))
    }

    @Test
    fun rowOf_unknownPostId_returnsNull() {
        assertNull(PostTable.of(SAMPLE_POSTS).rowOf(UNKNOWN_ID))
    }

    @Test
    fun rowsBy_author_returnsTheirRowsInTableOrder() {
        val table = PostTable.of(
            listOf(
                SAMPLE_POSTS[0].copy(id = "newer", authorUid = MAYA),
                SAMPLE_POSTS[1].copy(id = "other", authorUid = JORDAN),
                SAMPLE_POSTS[2].copy(id = "older", authorUid = MAYA)
            )
        )

        assertArrayEquals(intArrayOf(0, 2), table.rowsBy(MAYA))
    }

    @Test
    fun rowsBy_authorWithoutPosts_returnsNoRows() {
        assertArrayEquals(intArrayOf(), PostTable.of(SAMPLE_POSTS).rowsBy(MAYA))
    }

    @Test
    fun rowOf_refToRowOfSameTable_returnsRow() {
        val table = PostTable.of(SAMPLE_POSTS)

        assertEquals(1, table.rowOf(table.refTo(1)))
    }

    @Test
    fun rowOf_refFromEarlierTable_returnsNull() {
        val earlier = PostTable.of(SAMPLE_POSTS)
        val later = PostTable.of(SAMPLE_POSTS)

        assertNull(later.rowOf(earlier.refTo(1)))
    }

    @Test
    fun rowOf_refPastLastRow_returnsNull() {
        val table = PostTable.of(SAMPLE_POSTS)
        val pastLastRow = PostRef.of(generation = table.refTo(0).generation, row = table.size)

        assertNull(table.rowOf(pastLastRow))
    }

    @Test
    fun equals_tablesOfSamePosts_areEqual() {
        assertEquals(PostTable.of(SAMPLE_POSTS), PostTable.of(SAMPLE_POSTS))
    }

    @Test
    fun equals_tablesOfDifferentPosts_differ() {
        val edited = SAMPLE_POSTS.map { it.copy(caption = "edited") }

        assertNotEquals(PostTable.of(SAMPLE_POSTS), PostTable.of(edited))
    }

    @Test
    fun hashCode_tablesOfSamePosts_areEqual() {
        assertEquals(PostTable.of(SAMPLE_POSTS).hashCode(), PostTable.of(SAMPLE_POSTS).hashCode())
    }

    @Test
    fun build_fewerPostsThanCapacity_holdsOnlyAddedPosts() {
        val builder = PostTable.Builder(capacity = SAMPLE_POSTS.size)
        builder.add(SAMPLE_POSTS.first())

        assertEquals(PostTable.of(listOf(SAMPLE_POSTS.first())), builder.build())
    }

    @Test(expected = IllegalStateException::class)
    fun add_pastCapacity_throws() {
        val builder = PostTable.Builder(capacity = 1)
        builder.add(SAMPLE_POSTS[0])

        builder.add(SAMPLE_POSTS[1])
    }

    @Test
    fun build_beforeBuilderIsFull_tableIgnoresPostsAddedLater() {
        val builder = PostTable.Builder(capacity = 2)
        builder.add(SAMPLE_POSTS[0])
        val table = builder.build()

        builder.add(SAMPLE_POSTS[1].copy(isLiked = true, isBookmarked = true))

        assertEquals(PostTable.of(listOf(SAMPLE_POSTS[0])), table)
    }

    // The post a row holds, read back field by field.
    private fun PostTable.postAt(row: Int): Post = Post(
        id = id(row),
        authorUid = authorUid(row),
        username = username(row),
        avatar = Avatar(initials = avatarInitials(row)),
        locationName = locationName(row),
        latitude = latitude(row),
        longitude = longitude(row),
        imageUri = imageUri(row),
        caption = caption(row),
        likeCount = likeCount(row),
        commentCount = commentCount(row),
        isLiked = isLiked(row),
        isBookmarked = isBookmarked(row),
        timestamp = Instant.ofEpochMilli(timestampMillis(row)),
        animalType = animalType(row),
        confidence = confidence(row)
    )
}
