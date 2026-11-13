package edu.csuci.tweeter.data

import kotlinx.coroutines.flow.Flow

/**
 * Where posts are stored and read from.
 *
 * Post writes to it, and Feed, Map and Profile read from it. The app uses
 * [FirestorePostRepository]; screens only see this interface, so `AppContainer` can swap in
 * another, such as the in-memory [FakePostRepository], without changing any screen.
 *
 * To read posts in a ViewModel, turn the flow into state the screen can observe:
 * `val posts = postRepository.getPostTable().stateIn(viewModelScope, sharing, PostTable.EMPTY)`,
 * with `sharing` from `AppContainer.whileScreenCollects`, and keep rows of the table rather than
 * copies of its posts. To add one, call [addPost] from `viewModelScope.launch { }`.
 */
interface PostRepository {
    /**
     * All posts as one [PostTable], newest first. Emits a new table whenever a post is added or
     * changes, and only then: each table holds other posts than the one before it, so screens
     * tell tables apart by identity, without comparing their posts.
     */
    fun getPostTable(): Flow<PostTable>

    /** Stores [post] so it shows up first in [getPostTable]. */
    suspend fun addPost(post: Post)
}
