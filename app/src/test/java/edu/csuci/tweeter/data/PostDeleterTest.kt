@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.data

import com.google.firebase.FirebaseException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val USER_UID = "maya"
private const val OTHER_UID = "jordan"
private const val PHOTO_URL = "https://example.com/post.jpg"
private const val LOCAL_PHOTO_URI = "content://media/picture/1"
private const val UNKNOWN_POST_ID = "unknown"

class PostDeleterTest {
    private val mine = SAMPLE_POSTS.first().copy(
        id = "mine",
        authorUid = USER_UID,
        imageUri = PHOTO_URL
    )
    private val theirs = SAMPLE_POSTS.first().copy(
        id = "theirs",
        authorUid = OTHER_UID,
        imageUri = PHOTO_URL
    )
    private val sharedByGuest = SAMPLE_POSTS.first().copy(
        id = "guest",
        authorUid = "",
        imageUri = PHOTO_URL
    )
    private val posts = listOf(mine, theirs, sharedByGuest)
    private val postRepository = FakePostRepository(initialPosts = posts)
    private val photoRepository = FakePhotoRepository()

    @Test
    fun deleteOwnPost_writtenByLoggedInUser_deletesPostAndPhoto() = runTest {
        postDeleter(loggedInUid = USER_UID).deleteOwnPost(PostTable.of(posts), mine.id)

        assertEquals(listOf(theirs.id, sharedByGuest.id), storedPostIds())
        assertEquals(listOf(PHOTO_URL), photoRepository.deletedPhotoUrls)
    }

    @Test
    fun deleteOwnPost_writtenByAnotherUser_deletesNothing() = runTest {
        postDeleter(loggedInUid = USER_UID).deleteOwnPost(PostTable.of(posts), theirs.id)

        assertNothingDeleted()
    }

    @Test
    fun deleteOwnPost_sharedByGuest_deletesNothing() = runTest {
        postDeleter(loggedInUid = USER_UID).deleteOwnPost(PostTable.of(posts), sharedByGuest.id)

        assertNothingDeleted()
    }

    @Test
    fun deleteOwnPost_nobodyLoggedIn_deletesNothing() = runTest {
        postDeleter(loggedInUid = null).deleteOwnPost(PostTable.of(posts), sharedByGuest.id)

        assertNothingDeleted()
    }

    @Test
    fun deleteOwnPost_notInTable_deletesNothing() = runTest {
        postDeleter(loggedInUid = USER_UID).deleteOwnPost(PostTable.of(posts), UNKNOWN_POST_ID)

        assertNothingDeleted()
    }

    @Test
    fun deleteOwnPost_photoNotUploaded_deletesOnlyPost() = runTest {
        val withLocalPhoto = mine.copy(imageUri = LOCAL_PHOTO_URI)

        postDeleter(loggedInUid = USER_UID)
            .deleteOwnPost(PostTable.of(listOf(withLocalPhoto)), withLocalPhoto.id)

        assertEquals(listOf(theirs.id, sharedByGuest.id), storedPostIds())
        assertEquals(emptyList<String>(), photoRepository.deletedPhotoUrls)
    }

    @Test
    fun deleteOwnPost_postDeleteRejected_keepsPhoto() = runTest {
        val deleter = postDeleter(loggedInUid = USER_UID, repository = RejectingPostRepository())

        deleter.deleteOwnPost(PostTable.of(posts), mine.id)

        assertEquals(emptyList<String>(), photoRepository.deletedPhotoUrls)
    }

    @Test
    fun deleteOwnPost_serverAnswersLater_deletesPhotoOnceItDoes() = runTest {
        val serverAnswered = CompletableDeferred<Unit>()
        val repository = SlowPostRepository(serverAnswered)
        postDeleter(loggedInUid = USER_UID, repository).deleteOwnPost(PostTable.of(posts), mine.id)

        serverAnswered.complete(Unit)

        assertEquals(listOf(PHOTO_URL), photoRepository.deletedPhotoUrls)
    }

    // Deletes in a scope of its own, as the app's, which outlives the screen that asked.
    private fun TestScope.postDeleter(
        loggedInUid: String?,
        repository: PostRepository = postRepository
    ): PostDeleter = PostDeleter(
        postRepository = repository,
        photoRepository = photoRepository,
        accountRepository = FakeAccountRepository(initialUid = loggedInUid),
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
    )

    private suspend fun storedPostIds(): List<String> {
        val table = postRepository.getPostTable().first()
        return (0 until table.size).map(table::id)
    }

    private suspend fun assertNothingDeleted() {
        assertEquals(posts.map(Post::id), storedPostIds())
        assertEquals(emptyList<String>(), photoRepository.deletedPhotoUrls)
    }

    // Refuses every delete, as Firestore does when its rules reject it.
    private inner class RejectingPostRepository : PostRepository by postRepository {
        override suspend fun deletePost(postId: String): Unit =
            throw FirebaseException("Could not delete the post")
    }

    // Deletes once [serverAnswered] completes, as Firestore does once the server has the delete.
    private inner class SlowPostRepository(private val serverAnswered: CompletableDeferred<Unit>) :
        PostRepository by postRepository {
        override suspend fun deletePost(postId: String) {
            serverAnswered.await()
            postRepository.deletePost(postId)
        }
    }
}
