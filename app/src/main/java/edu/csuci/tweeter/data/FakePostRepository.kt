package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A [PostRepository] that keeps posts in memory, so screens can be built before there is a
 * backend. Posts added to it are lost when the app process ends.
 *
 * @param initialPosts The posts it starts with, newest first.
 */
class FakePostRepository(initialPosts: List<Post> = SAMPLE_POSTS) : PostRepository {
    private val posts = MutableStateFlow(initialPosts)

    override fun getPostTable(): Flow<PostTable> = posts.map(PostTable::of)

    override suspend fun addPost(post: Post) {
        posts.update { listOf(post) + it }
    }
}
