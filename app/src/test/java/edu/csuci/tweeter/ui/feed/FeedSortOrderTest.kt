package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.Avatar
import edu.csuci.tweeter.data.Post
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedSortOrderTest {
    @Test
    fun sortPosts_mostRecentFirst_ordersByDescendingTimestamp() {
        val olderPost = createTestPost(id = "1", timestamp = Instant.ofEpochSecond(1000))
        val newerPost = createTestPost(id = "2", timestamp = Instant.ofEpochSecond(2000))

        val sorted = listOf(olderPost, newerPost).sortPosts(FeedSortOrder.MOST_RECENT_FIRST)

        assertEquals(listOf(newerPost, olderPost), sorted)
    }

    @Test
    fun sortPosts_oldestFirst_ordersByAscendingTimestamp() {
        val olderPost = createTestPost(id = "1", timestamp = Instant.ofEpochSecond(1000))
        val newerPost = createTestPost(id = "2", timestamp = Instant.ofEpochSecond(2000))

        val sorted = listOf(newerPost, olderPost).sortPosts(FeedSortOrder.OLDEST_FIRST)

        assertEquals(listOf(olderPost, newerPost), sorted)
    }

    private fun createTestPost(id: String, timestamp: Instant): Post = Post(
        id = id,
        authorUid = "",
        username = "testuser",
        avatar = Avatar(initials = "TU"),
        locationName = "Test Place",
        latitude = 0.0,
        longitude = 0.0,
        imageUri = "https://example.com/photo.jpg",
        caption = "Test photo",
        likeCount = 0,
        commentCount = 0,
        isLiked = false,
        isBookmarked = false,
        timestamp = timestamp
    )
}
