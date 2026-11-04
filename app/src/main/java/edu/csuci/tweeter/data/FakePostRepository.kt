package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    override fun getPosts(): Flow<List<Post>> = posts.asStateFlow()

    override fun getPostsBy(authorUid: String): Flow<List<Post>> =
        posts.map { allPosts -> allPosts.filter { it.authorUid == authorUid } }

    override suspend fun addPost(post: Post) {
        posts.update { listOf(post) + it }
    }
}
