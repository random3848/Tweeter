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
private const val SAM = "sam"

private const val MOST_CONFIDENT = 5
private const val LIKE_COUNT = 12
private const val RACCOON = "Raccoon"
private const val POSSUM = "Possum"
private const val VOTE_ROUND = 2

class PostTableTest {
    // The table keeps a post's time to the millisecond.
    private val posts = SAMPLE_POSTS.map { it.copy(timestamp = it.timestamp.truncatedTo(MILLIS)) }

    @Test
    fun of_posts_eachRowHoldsItsPostsShownFields() {
        val table = PostTable.of(posts)

        assertEquals(
            posts.map { it.withFieldsNotReadBackCleared() },
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
    fun likeCount_isPostsLikeCount() {
        val table = PostTable.of(listOf(SAMPLE_POSTS.first().copy(likeCount = LIKE_COUNT)))

        assertEquals(LIKE_COUNT, table.likeCount(0))
    }

    @Test
    fun shownAnimal_noOtherAnimalHasMajority_isPostersAnimal() {
        val table = tableOfVotes(RACCOON, mapOf(POSSUM to 1))

        assertEquals(RACCOON, table.shownAnimal(0))
    }

    @Test
    fun shownAnimal_otherAnimalHasMajority_isThatAnimal() {
        val table = tableOfVotes(RACCOON, mapOf(POSSUM to 2))

        assertEquals(POSSUM, table.shownAnimal(0))
    }

    @Test
    fun agreement_isShareOfVotesForShownAnimal() {
        val table = tableOfVotes(RACCOON, mapOf(RACCOON to 2, POSSUM to 1))

        assertEquals(0.75, table.agreement(0), 0.0)
    }

    @Test
    fun voteRound_isPostsVoteRound() {
        val table = PostTable.of(listOf(SAMPLE_POSTS.first().copy(voteRound = VOTE_ROUND)))

        assertEquals(VOTE_ROUND, table.voteRound(0))
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
    fun rowsByLikeCount_mostLikedFirst() {
        val table = tableOfLikeCounts(1, 5, 0, 3)

        assertArrayEquals(intArrayOf(1, 3, 0, 2), table.rowsByLikeCount())
    }

    @Test
    fun rowsByLikeCount_equalLikeCounts_keepsTableOrder() {
        val table = tableOfLikeCounts(2, 7, 2, 2)

        assertArrayEquals(intArrayOf(1, 0, 2, 3), table.rowsByLikeCount())
    }

    @Test
    fun rowsByDescending_highestScoreFirst() {
        val table = tableOfLikeCounts(0, 0, 0)
        val scores = doubleArrayOf(0.5, 2.0, 1.0)

        assertArrayEquals(intArrayOf(1, 2, 0), table.rowsByDescending { row -> scores[row] })
    }

    @Test
    fun rowsByDescending_scoresEachRowOnce() {
        val table = PostTable.of(SAMPLE_POSTS)
        var scoreCount = 0

        table.rowsByDescending { row ->
            scoreCount++
            row.toDouble()
        }

        assertEquals(table.size, scoreCount)
    }

    @Test
    fun rowsByLikeCount_noPosts_returnsNoRows() {
        assertArrayEquals(intArrayOf(), PostTable.EMPTY.rowsByLikeCount())
    }

    @Test
    fun rowsByLikeCount_postAddedAfterBuild_leavesItOut() {
        val builder = builderWithRoomAfter(SAMPLE_POSTS[0])
        val table = builder.build()

        builder.add(SAMPLE_POSTS[1].copy(likeCount = LIKE_COUNT))

        assertArrayEquals(intArrayOf(0), table.rowsByLikeCount())
    }

    @Test
    fun rowsBy_authors_returnsAllTheirRowsInTableOrder() {
        val table = PostTable.of(
            listOf(
                SAMPLE_POSTS[0].copy(id = "maya's", authorUid = MAYA),
                SAMPLE_POSTS[1].copy(id = "jordan's", authorUid = JORDAN),
                SAMPLE_POSTS[2].copy(id = "sam's", authorUid = SAM)
            )
        )

        assertArrayEquals(intArrayOf(0, 2), table.rowsBy(setOf(SAM, MAYA)))
    }

    @Test
    fun rowsBy_noAuthors_returnsNoRows() {
        assertArrayEquals(intArrayOf(), PostTable.of(SAMPLE_POSTS).rowsBy(emptySet()))
    }

    @Test
    fun rowsOfAnimal_matchesAnimalIgnoringCaseAndSurroundingSpaces() {
        val table = PostTable.of(
            listOf(
                SAMPLE_POSTS[0].copy(id = "spaced", animalType = " Island fox "),
                SAMPLE_POSTS[1].copy(id = "other", animalType = "Coyote"),
                SAMPLE_POSTS[2].copy(id = "lower", animalType = "island fox")
            )
        )

        assertArrayEquals(intArrayOf(0, 2), table.rowsOfAnimal("Island Fox"))
    }

    @Test
    fun rowsOfAnimal_blankAnimal_matchesNoPosts() {
        val table = PostTable.of(listOf(SAMPLE_POSTS[0].copy(animalType = "")))

        assertArrayEquals(intArrayOf(), table.rowsOfAnimal(" "))
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
    fun holdsSamePosts_postLiked_false() {
        val liked = SAMPLE_POSTS.map { it.copy(likeCount = it.likeCount + 1) }

        assertFalse(PostTable.of(SAMPLE_POSTS).holdsSamePosts(PostTable.of(liked)))
    }

    @Test
    fun holdsSamePosts_majoritySwitchesShownAnimal_false() {
        val switched = tableOfVotes(RACCOON, mapOf(POSSUM to 2))

        assertFalse(tableOfVotes(RACCOON, emptyMap()).holdsSamePosts(switched))
    }

    @Test
    fun holdsSamePosts_voteChangesAgreement_false() {
        val disputed = tableOfVotes(RACCOON, mapOf(POSSUM to 1))

        assertFalse(tableOfVotes(RACCOON, emptyMap()).holdsSamePosts(disputed))
    }

    @Test
    fun holdsSamePosts_agreeingVoteOnUndisputedPost_true() {
        val agreed = tableOfVotes(RACCOON, mapOf(RACCOON to 1))

        assertTrue(tableOfVotes(RACCOON, emptyMap()).holdsSamePosts(agreed))
    }

    @Test
    fun holdsSamePosts_postReclassified_false() {
        val reclassified = SAMPLE_POSTS.map { it.copy(voteRound = it.voteRound + 1) }

        assertFalse(PostTable.of(SAMPLE_POSTS).holdsSamePosts(PostTable.of(reclassified)))
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

    // A table with one post per like count, in the order given.
    private fun tableOfLikeCounts(vararg likeCounts: Int): PostTable = PostTable.of(
        likeCounts.mapIndexed { row, likeCount ->
            SAMPLE_POSTS[0].copy(id = "post$row", likeCount = likeCount)
        }
    )

    // A table of one post of [posterAnimal] with [voteCounts].
    private fun tableOfVotes(posterAnimal: String, voteCounts: Map<String, Int>): PostTable =
        PostTable.of(
            listOf(SAMPLE_POSTS[0].copy(animalType = posterAnimal, voteCounts = voteCounts))
        )

    // A builder holding [post], with room for one more.
    private fun builderWithRoomAfter(post: Post): PostTable.Builder {
        val builder = PostTable.Builder(capacity = 2)
        builder.add(post)
        return builder
    }

    // The post a row holds, read back field by field, with the fields the table doesn't give
    // back cleared.
    private fun PostTable.postAt(row: Int): Post = Post(
        id = id(row),
        authorUid = "",
        username = username(row),
        avatar = Avatar(initials = avatarInitials(row)),
        locationName = locationName(row),
        latitude = latitude(row),
        longitude = longitude(row),
        imageUri = imageUri(row),
        caption = caption(row),
        likeCount = likeCount(row),
        commentCount = 0,
        isLiked = false,
        isBookmarked = false,
        timestamp = Instant.ofEpochMilli(timestampMillis(row)),
        animalType = animalType(row),
        confidence = confidence(row),
        voteRound = voteRound(row)
    )

    // No screen shows comments or bookmarks yet, and whether the user liked a post depends on who
    // is looking, so the table doesn't keep them. It keeps authors only to find their rows with
    // rowsBy, which the tests of rowsBy cover, and what the vote counts mean rather than the
    // counts, which the tests of shownAnimal and agreement cover.
    private fun Post.withFieldsNotReadBackCleared(): Post = copy(
        authorUid = "",
        commentCount = 0,
        isLiked = false,
        isBookmarked = false,
        voteCounts = emptyMap()
    )
}
