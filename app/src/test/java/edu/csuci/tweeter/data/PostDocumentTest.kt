package edu.csuci.tweeter.data

import com.google.firebase.Timestamp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostDocumentTest {
    private val post = SAMPLE_POSTS.first().copy(
        isLiked = false,
        isBookmarked = false,
        timestamp = Instant.ofEpochSecond(1_760_000_000, 123_000_000)
    )

    @Test
    fun toPost_fieldsOfPost_returnsSamePost() {
        val fields = PostDocument.fieldsOf(post)

        assertEquals(post, PostDocument.toPost(post.id, fields))
    }

    @Test
    fun toPost_likedAndBookmarked_readsBackAsFalse() {
        val fields = PostDocument.fieldsOf(post.copy(isLiked = true, isBookmarked = true))

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
        val fields = PostDocument.fieldsOf(post) - "timestamp"

        assertNull(PostDocument.toPost(post.id, fields))
    }

    @Test
    fun toPost_latitudeIsText_returnsNull() {
        val fields = PostDocument.fieldsOf(post) + ("latitude" to "34.16")

        assertNull(PostDocument.toPost(post.id, fields))
    }
}
