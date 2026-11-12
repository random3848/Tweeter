package edu.csuci.tweeter.data

import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val UNKNOWN_ID = "no-such-post"
private const val MAYA = "maya"
private const val JORDAN = "jordan"

private const val MOST_CONFIDENT = 5

class PostTableTest {
    // The table keeps a post's time to the millisecond.
    private val posts = SAMPLE_POSTS.map { it.copy(timestamp = it.timestamp.truncatedTo(MILLIS)) }

    @Test
    fun of_posts_eachRowHoldsItsPostsShownFields() {
        val table = PostTable.of(posts)

        assertEquals(
            posts.map { it.withUnshownFieldsCleared() },
            posts.indices.map { table.postAt(it) }
        )
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
    fun rowOf_postAddedAfterBuild_returnsNull() {
        val builder = builderWithRoomAfter(SAMPLE_POSTS[0])
        val table = builder.build()

        builder.add(SAMPLE_POSTS[1])

        assertNull(table.rowOf(SAMPLE_POSTS[1].id))
    }

    @Test
    fun rowsBy_postAddedAfterBuild_leavesItOut() {
        val builder = builderWithRoomAfter(SAMPLE_POSTS[0].copy(authorUid = MAYA))
        val table = builder.build()

        builder.add(SAMPLE_POSTS[1].copy(authorUid = MAYA))

        assertArrayEquals(intArrayOf(0), table.rowsBy(MAYA))
    }

    @Test
    fun holdsSamePosts_tablesOfSamePosts_true() {
        assertTrue(PostTable.of(SAMPLE_POSTS).holdsSamePosts(PostTable.of(SAMPLE_POSTS)))
    }

    @Test
    fun holdsSamePosts_postEdited_false() {
        val edited = SAMPLE_POSTS.map { it.copy(caption = "edited") }

        assertFalse(PostTable.of(SAMPLE_POSTS).holdsSamePosts(PostTable.of(edited)))
    }

    @Test
    fun holdsSamePosts_postRemoved_false() {
        val fewer = PostTable.of(SAMPLE_POSTS.drop(1))

        assertFalse(PostTable.of(SAMPLE_POSTS).holdsSamePosts(fewer))
    }

    @Test
    fun holdsSamePosts_tableBuiltWithRoomLeft_matchesFullTable() {
        val full = PostTable.of(listOf(SAMPLE_POSTS[0]))

        assertTrue(full.holdsSamePosts(builderWithRoomAfter(SAMPLE_POSTS[0]).build()))
    }

    @Test(expected = IllegalStateException::class)
    fun add_pastCapacity_throws() {
        val builder = PostTable.Builder(capacity = 1)
        builder.add(SAMPLE_POSTS[0])

        builder.add(SAMPLE_POSTS[1])
    }

    @Test
    fun build_beforeBuilderIsFull_tableIgnoresPostsAddedLater() {
        val builder = builderWithRoomAfter(SAMPLE_POSTS[0])
        val table = builder.build()

        builder.add(SAMPLE_POSTS[1])

        assertSamePosts(PostTable.of(listOf(SAMPLE_POSTS[0])), table)
    }

    // A builder holding [post], with room for one more.
    private fun builderWithRoomAfter(post: Post): PostTable.Builder {
        val builder = PostTable.Builder(capacity = 2)
        builder.add(post)
        return builder
    }

    // The post a row holds, read back field by field, with the fields the table doesn't keep
    // cleared.
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
        likeCount = 0,
        commentCount = 0,
        isLiked = false,
        isBookmarked = false,
        timestamp = Instant.ofEpochMilli(timestampMillis(row)),
        animalType = animalType(row),
        confidence = confidence(row)
    )

    // No screen shows these fields yet, so the table doesn't keep them.
    private fun Post.withUnshownFieldsCleared(): Post =
        copy(likeCount = 0, commentCount = 0, isLiked = false, isBookmarked = false)
}
