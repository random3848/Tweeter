package edu.csuci.tweeter.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.time.Instant
import org.junit.Assert.assertEquals
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
    fun toPost_fieldsOfPost_returnsSamePost() {
        val fields = storedFieldsOf(post)

        assertEquals(post, PostDocument.toPost(post.id, fields))
    }

    @Test
    fun toPost_likedAndBookmarked_readsBackAsFalse() {
        val fields = storedFieldsOf(post.copy(isLiked = true, isBookmarked = true))

        assertEquals(post, PostDocument.toPost(post.id, fields))
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

        val result = PostDocument.toPost("id", fields)

        assertEquals(
            Post(
                id = "id",
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

        assertNull(PostDocument.toPost(post.id, fields))
    }

    @Test
    fun toPost_latitudeIsText_returnsNull() {
        val fields = storedFieldsOf(post) + ("latitude" to "34.16")

        assertNull(PostDocument.toPost(post.id, fields))
    }

    // The fields as Firestore stores them, once it has set the timestamp to post.timestamp.
    private fun storedFieldsOf(post: Post): Map<String, Any> =
        PostDocument.fieldsOf(post) + ("timestamp" to Timestamp(post.timestamp))
}
