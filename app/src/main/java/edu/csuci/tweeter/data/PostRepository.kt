package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow

/**
 * Where posts are stored and read from.
 *
 * Post writes to it, and Feed and Map read from it. The app uses [FirestorePostRepository];
 * screens only see this interface, so `AppContainer` can swap in another, such as the in-memory
 * [FakePostRepository], without changing any screen.
 *
 * To read posts in a ViewModel, turn the flow into state the screen can observe:
 * `val posts = postRepository.getPosts().stateIn(viewModelScope, WhileSubscribed(5_000),
 * emptyList())`. To add one, call [addPost] from `viewModelScope.launch { }`.
 */
interface PostRepository {
    /** All posts, newest first. Emits again whenever a post is added. */
    fun getPosts(): Flow<List<Post>>

    /** Stores [post] so it shows up first in [getPosts]. */
    suspend fun addPost(post: Post)
}
