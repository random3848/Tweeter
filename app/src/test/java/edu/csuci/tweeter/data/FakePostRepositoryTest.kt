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
    fun addPost_emptyRepository_containsOnlyNewPost() = runBlocking {
        val repository = FakePostRepository(initialPosts = emptyList())
        val post = SAMPLE_POSTS.first()

        repository.addPost(post)

        assertEquals(listOf(post), repository.getPosts().first())
    }
}
