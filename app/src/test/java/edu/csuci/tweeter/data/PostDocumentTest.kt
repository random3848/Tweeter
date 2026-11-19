package edu.csuci.tweeter.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import edu.csuci.tweeter.data.PostDocument.Field
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostDocumentTest {
    private val post = SAMPLE_POSTS.first().copy(
        isLiked = false,
        isBookmarked = false,
        timestamp = Instant.ofEpochSecond(1_760_000_000, 123_000_000)
    )

    @Test
    fun fieldsOf_post_leavesTimestampForFirestore() {
        val fields = PostDocument.fieldsOf(post)

        assertTrue(fields["timestamp"] is FieldValue)
    }

    @Test
    fun fieldsOf_noAuthor_leavesOutAuthorUid() {
        val fields = PostDocument.fieldsOf(post.copy(authorUid = ""))

        assertFalse("authorUid" in fields)
    }

    @Test
    fun toPost_fieldsOfPostWithAuthor_returnsSamePost() {
        val postWithAuthor = post.copy(authorUid = "uid")
        val fields = storedFieldsOf(postWithAuthor)

        assertEquals(postWithAuthor, PostDocument.toPost(post.id, Field.entries.readFrom(fields)))
    }

    @Test
    fun toPost_fieldsOfPost_returnsSamePost() {
        val fields = storedFieldsOf(post)

        assertEquals(post, PostDocument.toPost(post.id, Field.entries.readFrom(fields)))
    }

    @Test
    fun toPost_likedAndBookmarked_readsBackAsFalse() {
        val fields = storedFieldsOf(post.copy(isLiked = true, isBookmarked = true))

        assertEquals(post, PostDocument.toPost(post.id, Field.entries.readFrom(fields)))
    }

    @Test
    fun toPost_onlyRequiredFields_fillsDefaults() {
        val fields = mapOf(
            "username" to "maya.k",
            "latitude" to 34L,
            "longitude" to -119.04,
            "imageUri" to "https://example.com/photo.jpg",
            "timestamp" to Timestamp(post.timestamp)
        )

        val result = PostDocument.toPost("id", Field.entries.readFrom(fields))

        assertEquals(
            Post(
                id = "id",
                authorUid = "",
                username = "maya.k",
                avatar = Avatar(initials = "M"),
                locationName = "",
                latitude = 34.0,
                longitude = -119.04,
                imageUri = "https://example.com/photo.jpg",
                caption = "",
                likeCount = 0,
                commentCount = 0,
                isLiked = false,
                isBookmarked = false,
                timestamp = post.timestamp
            ),
            result
        )
    }

    @Test
    fun toPost_missingTimestamp_returnsNull() {
        val fields = storedFieldsOf(post) - "timestamp"

        assertNull(PostDocument.toPost(post.id, Field.entries.readFrom(fields)))
    }

    @Test
    fun toPost_latitudeIsText_returnsNull() {
        val fields = storedFieldsOf(post) + ("latitude" to "34.16")

        assertNull(PostDocument.toPost(post.id, Field.entries.readFrom(fields)))
    }

    @Test
    fun toPost_confidenceOutOfRange_dropsConfidence() {
        val fields = storedFieldsOf(post) + ("confidence" to OUT_OF_RANGE_CONFIDENCE)

        assertNull(PostDocument.toPost(post.id, Field.entries.readFrom(fields))?.confidence)
    }

    @Test
    fun toPost_namedAnimal_keepsConfidence() {
        val namedPost = post.copy(animalType = "Island fox", confidence = Post.MAX_CONFIDENCE)
        val fields = storedFieldsOf(namedPost)

        assertEquals(namedPost, PostDocument.toPost(post.id, Field.entries.readFrom(fields)))
    }

    @Test
    fun toPost_noAnimal_dropsConfidence() {
        val fields = storedFieldsOf(post.copy(animalType = "", confidence = Post.MAX_CONFIDENCE))

        assertNull(PostDocument.toPost(post.id, Field.entries.readFrom(fields))?.confidence)
    }

    @Test
    fun toPost_missingUsername_returnsNull() {
        val fields = storedFieldsOf(post) - "username"

        assertNull(PostDocument.toPost(post.id, Field.entries.readFrom(fields)))
    }

    @Test
    fun toPost_fieldsOfPostWithVotes_returnsSamePost() {
        val postWithVotes = post.copy(voteCounts = mapOf(RACCOON to 2), voteRound = VOTE_ROUND)
        val fields = storedFieldsOf(postWithVotes)

        assertEquals(postWithVotes, PostDocument.toPost(post.id, Field.entries.readFrom(fields)))
    }

    @Test
    fun toPost_voteCountsStoredAsLongs_readsThem() {
        val fields = storedFieldsOf(post) + ("voteCounts" to mapOf(RACCOON to 2L))

        val result = PostDocument.toPost(post.id, Field.entries.readFrom(fields))

        assertEquals(mapOf(RACCOON to 2), result?.voteCounts)
    }

    @Test
    fun toPost_voteCountsNotWholePositiveNumbers_dropsThem() {
        val voteCounts = mapOf(RACCOON to 0L, POSSUM to "2", COYOTE to -1L)
        val fields = storedFieldsOf(post) + ("voteCounts" to voteCounts)

        val result = PostDocument.toPost(post.id, Field.entries.readFrom(fields))

        assertEquals(emptyMap<String, Int>(), result?.voteCounts)
    }

    @Test
    fun toPost_voteCountsNotMap_readsNoVotes() {
        val fields = storedFieldsOf(post) + ("voteCounts" to "Raccoon")

        val result = PostDocument.toPost(post.id, Field.entries.readFrom(fields))

        assertEquals(emptyMap<String, Int>(), result?.voteCounts)
    }

    @Test
    fun reclassifiedFields_namesAnimalAndClearsVotes() {
        val fields = PostDocument.reclassifiedFields(POSSUM)

        assertEquals(POSSUM, fields["animalType"])
        assertEquals(emptyMap<String, Int>(), fields["voteCounts"])
    }

    @Test
    fun reclassifiedFields_removesConfidence() {
        val fields = PostDocument.reclassifiedFields(POSSUM)

        assertEquals(FieldValue.delete(), fields["confidence"])
    }

    @Test
    fun reclassifiedFields_leavesNextRoundForFirestore() {
        val fields = PostDocument.reclassifiedFields(POSSUM)

        // Firestore adds one to the round it holds; FieldValue can't tell which change it makes.
        assertTrue(fields["voteRound"] is FieldValue)
    }

    // The fields as Firestore stores them, once it has set the timestamp to post.timestamp.
    private fun storedFieldsOf(post: Post): Map<String, Any> =
        PostDocument.fieldsOf(post) + ("timestamp" to Timestamp(post.timestamp))

    private companion object {
        const val OUT_OF_RANGE_CONFIDENCE = 6L
        const val VOTE_ROUND = 3
        const val RACCOON = "Raccoon"
        const val POSSUM = "Possum"
        const val COYOTE = "Coyote"
    }
}
