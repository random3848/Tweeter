package edu.csuci.tweeter.data

import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FakePostRepositoryTest {
    @Test
    fun getPosts_startsWithSamplePosts() = runBlocking {
        val repository = FakePostRepository()

        assertEquals(SAMPLE_POSTS, repository.getPosts().first())
    }

    @Test
    fun addPost_putsNewPostFirst() = runBlocking {
        val repository = FakePostRepository()
        val post = SAMPLE_POSTS.first().copy(id = "new", timestamp = Instant.now())

        repository.addPost(post)

        assertEquals(listOf(post) + SAMPLE_POSTS, repository.getPosts().first())
    }

    @Test
    fun getPostsBy_returnsOnlyThatAuthorsPostsInOrder() = runBlocking {
        val newer = SAMPLE_POSTS[0].copy(id = "newer", authorUid = "maya")
        val other = SAMPLE_POSTS[1].copy(id = "other", authorUid = "jordan")
        val older = SAMPLE_POSTS[2].copy(id = "older", authorUid = "maya")
        val repository = FakePostRepository(initialPosts = listOf(newer, other, older))

        assertEquals(listOf(newer, older), repository.getPostsBy("maya").first())
    }

    @Test
    fun addPost_emptyRepository_containsOnlyNewPost() = runBlocking {
        val repository = FakePostRepository(initialPosts = emptyList())
        val post = SAMPLE_POSTS.first()

        repository.addPost(post)

        assertEquals(listOf(post), repository.getPosts().first())
    }
}
