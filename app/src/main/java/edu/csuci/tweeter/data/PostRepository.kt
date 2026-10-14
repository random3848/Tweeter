package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow

/**
 * Where posts are stored and read from.
 *
 * Post writes to it, and Feed and Map read from it. Screens only see this interface, so the
 * in-memory [FakePostRepository] can be swapped for a real backend in `AppContainer` without
 * changing any screen.
 */
interface PostRepository {
    /** All posts, newest first. Emits again whenever a post is added. */
    fun getPosts(): Flow<List<Post>>

    /** Stores [post] so it shows up first in [getPosts]. */
    suspend fun addPost(post: Post)
}
