package edu.csuci.tweeter.ui.feed

import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostTable
import edu.csuci.tweeter.data.SAMPLE_POSTS
import org.junit.Assert.assertEquals
import org.junit.Test

private const val NOW_MILLIS = 0L
private const val NEW_ID = "new"
private const val LESS_LIKED_ID = "less-liked"
private const val MORE_LIKED_ID = "more-liked"

class RankedPostsTest {
    // Newest first, as the repository gives them.
    private val lessLiked = postWith(LESS_LIKED_ID, likeCount = 1)
    private val moreLiked = postWith(MORE_LIKED_ID, likeCount = 5)

    @Test
    fun of_putsPostsInOrder() {
        val ranked = RankedPosts.of(tableOf(lessLiked, moreLiked), FeedOrder.TOP, NOW_MILLIS)

        assertEquals(listOf(MORE_LIKED_ID, LESS_LIKED_ID), ranked.shownPostIds())
    }

    @Test
    fun withPosts_likesChanged_keepsPostsWhereTheyWere() {
        val ranked = RankedPosts.of(tableOf(lessLiked, moreLiked), FeedOrder.TOP, NOW_MILLIS)
        val liked = tableOf(lessLiked.copy(likeCount = 9), moreLiked)

        assertEquals(
            listOf(MORE_LIKED_ID, LESS_LIKED_ID),
            ranked.withPosts(liked).shownPostIds()
        )
    }

    @Test
    fun withPosts_newPost_putsItFirst() {
        val ranked = RankedPosts.of(tableOf(lessLiked, moreLiked), FeedOrder.TOP, NOW_MILLIS)
        val withNewPost = tableOf(postWith(NEW_ID, likeCount = 0), lessLiked, moreLiked)

        assertEquals(
            listOf(NEW_ID, MORE_LIKED_ID, LESS_LIKED_ID),
            ranked.withPosts(withNewPost).shownPostIds()
        )
    }

    @Test
    fun withPosts_postDeleted_dropsIt() {
        val ranked = RankedPosts.of(tableOf(lessLiked, moreLiked), FeedOrder.TOP, NOW_MILLIS)

        assertEquals(
            listOf(LESS_LIKED_ID),
            ranked.withPosts(tableOf(lessLiked)).shownPostIds()
        )
    }

    @Test
    fun withPosts_keepsOrder() {
        val ranked = RankedPosts.of(tableOf(lessLiked, moreLiked), FeedOrder.TOP, NOW_MILLIS)

        assertEquals(FeedOrder.TOP, ranked.withPosts(tableOf(lessLiked)).order)
    }

    private fun postWith(id: String, likeCount: Int): Post =
        SAMPLE_POSTS[0].copy(id = id, likeCount = likeCount)

    private fun tableOf(vararg posts: Post): PostTable = PostTable.of(posts.toList())

    private fun RankedPosts.shownPostIds(): List<String> =
        listedPosts.rows.map(listedPosts.table::id)
}
