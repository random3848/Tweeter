@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.ui.post

import edu.csuci.tweeter.data.UserProfile
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PHOTO_URL = "https://example.com/photo.jpg"
private const val SHORT_WAIT_MILLIS = 1_000L
private const val LONG_WAIT_MILLIS = 2_000L

class PostSharingTest {
    private val author = UserProfile(uid = "ada", displayName = "Ada", bio = "", photoUrl = "")

    @Test
    fun sharePost_slowUpload_findsAuthorMeanwhile() = runTest {
        sharePost(
            photo = FakePostPhoto { uploadTaking(LONG_WAIT_MILLIS) },
            findAuthor = { authorFoundAfter(SHORT_WAIT_MILLIS) },
            addPost = { _, _ -> }
        )

        assertEquals(LONG_WAIT_MILLIS, testScheduler.currentTime)
    }

    @Test
    fun sharePost_slowAuthorLookup_addsPostOnceAuthorIsFound() = runTest {
        var postAddedAtMillis: Long? = null

        sharePost(
            photo = FakePostPhoto { uploadTaking(SHORT_WAIT_MILLIS) },
            findAuthor = { authorFoundAfter(LONG_WAIT_MILLIS) },
            addPost = { _, _ -> postAddedAtMillis = testScheduler.currentTime }
        )

        assertEquals(LONG_WAIT_MILLIS, postAddedAtMillis)
    }

    @Test
    fun sharePost_addsPostWithUploadedPhotoAndAuthor() = runTest {
        var addedPost: Pair<String, UserProfile>? = null

        sharePost(
            photo = FakePostPhoto { PHOTO_URL },
            findAuthor = { author },
            addPost = { photoUrl, postAuthor -> addedPost = photoUrl to postAuthor }
        )

        assertEquals(PHOTO_URL to author, addedPost)
    }

    @Test
    fun sharePost_returnsUploadedPhotoUrl() = runTest {
        val photoUrl = sharePost(
            photo = FakePostPhoto { PHOTO_URL },
            findAuthor = { author },
            addPost = { _, _ -> }
        )

        assertEquals(PHOTO_URL, photoUrl)
    }

    @Test
    fun sharePost_postAdded_deletesNothing() = runTest {
        val photo = FakePostPhoto { PHOTO_URL }

        sharePost(photo = photo, findAuthor = { author }, addPost = { _, _ -> })

        assertEquals(emptyList<String>(), photo.deletedPhotoUrls)
    }

    @Test
    fun sharePost_uploadFails_throwsUploadErrorWithoutAddingPost() = runTest {
        var isPostAdded = false

        val error = runCatching {
            sharePost(
                photo = FakePostPhoto { throw IOException("Upload failed") },
                findAuthor = { authorFoundAfter(SHORT_WAIT_MILLIS) },
                addPost = { _, _ -> isPostAdded = true }
            )
        }.exceptionOrNull()

        assertEquals(IOException::class, error?.let { it::class })
        assertFalse(isPostAdded)
    }

    @Test
    fun sharePost_authorLookupFails_throwsLookupErrorWithoutAddingPost() = runTest {
        var isPostAdded = false

        val error = runCatching {
            sharePost(
                photo = FakePostPhoto { uploadTaking(SHORT_WAIT_MILLIS) },
                findAuthor = { throw IllegalStateException("Profile unreadable") },
                addPost = { _, _ -> isPostAdded = true }
            )
        }.exceptionOrNull()

        assertEquals(IllegalStateException::class, error?.let { it::class })
        assertFalse(isPostAdded)
    }

    @Test
    fun sharePost_authorLookupFailsAfterUpload_deletesPhoto() = runTest {
        val photo = FakePostPhoto { PHOTO_URL }

        runCatching {
            sharePost(
                photo = photo,
                findAuthor = {
                    delay(SHORT_WAIT_MILLIS)
                    throw IllegalStateException("Profile unreadable")
                },
                addPost = { _, _ -> }
            )
        }

        assertEquals(listOf(PHOTO_URL), photo.deletedPhotoUrls)
    }

    @Test
    fun sharePost_nobodyLoggedIn_throwsWithoutAddingPost() = runTest {
        var isPostAdded = false

        val error = runCatching {
            sharePost(
                photo = FakePostPhoto { uploadTaking(SHORT_WAIT_MILLIS) },
                findAuthor = { null },
                addPost = { _, _ -> isPostAdded = true }
            )
        }.exceptionOrNull()

        assertEquals(NobodyLoggedInException::class, error?.let { it::class })
        assertFalse(isPostAdded)
    }

    @Test
    fun sharePost_nobodyFoundAfterUpload_deletesPhotoAndThrows() = runTest {
        val photo = FakePostPhoto { PHOTO_URL }

        val error = runCatching {
            sharePost(
                photo = photo,
                findAuthor = {
                    delay(SHORT_WAIT_MILLIS)
                    null
                },
                addPost = { _, _ -> }
            )
        }.exceptionOrNull()

        assertEquals(NobodyLoggedInException::class, error?.let { it::class })
        assertEquals(listOf(PHOTO_URL), photo.deletedPhotoUrls)
    }

    @Test
    fun sharePost_nobodyFoundMidUpload_cancelsUpload() = runTest {
        var isUploadCancelled = false

        runCatching {
            sharePost(
                photo = FakePostPhoto {
                    try {
                        uploadTaking(LONG_WAIT_MILLIS)
                    } catch (error: CancellationException) {
                        isUploadCancelled = true
                        throw error
                    }
                },
                findAuthor = {
                    delay(SHORT_WAIT_MILLIS)
                    null
                },
                addPost = { _, _ -> }
            )
        }

        assertTrue(isUploadCancelled)
    }

    @Test
    fun sharePost_nobodyFoundMidUpload_deletesNothing() = runTest {
        val photo = FakePostPhoto { uploadTaking(LONG_WAIT_MILLIS) }

        runCatching {
            sharePost(
                photo = photo,
                findAuthor = {
                    delay(SHORT_WAIT_MILLIS)
                    null
                },
                addPost = { _, _ -> }
            )
        }

        assertEquals(emptyList<String>(), photo.deletedPhotoUrls)
    }

    @Test
    fun sharePost_addPostFails_deletesPhotoAndRethrows() = runTest {
        val photo = FakePostPhoto { PHOTO_URL }

        val error = runCatching {
            sharePost(
                photo = photo,
                findAuthor = { author },
                addPost = { _, _ -> throw IOException("Could not add the post") }
            )
        }.exceptionOrNull()

        assertEquals(IOException::class, error?.let { it::class })
        assertEquals(listOf(PHOTO_URL), photo.deletedPhotoUrls)
    }

    @Test
    fun sharePost_cancelledAfterUpload_deletesPhoto() = runTest {
        val photo = FakePostPhoto { uploadTaking(SHORT_WAIT_MILLIS) }
        val sharing = launch {
            sharePost(
                photo = photo,
                findAuthor = { authorFoundAfter(LONG_WAIT_MILLIS) },
                addPost = { _, _ -> }
            )
        }
        testScheduler.advanceTimeBy(LONG_WAIT_MILLIS - 1)

        sharing.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(PHOTO_URL), photo.deletedPhotoUrls)
    }

    @Test
    fun sharePost_cancelledWhileAddingPost_keepsPhoto() = runTest {
        val photo = FakePostPhoto { PHOTO_URL }
        val sharing = launch {
            sharePost(
                photo = photo,
                findAuthor = { author },
                addPost = { _, _ -> delay(LONG_WAIT_MILLIS) }
            )
        }
        testScheduler.advanceTimeBy(SHORT_WAIT_MILLIS)

        sharing.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList<String>(), photo.deletedPhotoUrls)
    }

    private suspend fun uploadTaking(millis: Long): String {
        delay(millis)
        return PHOTO_URL
    }

    private suspend fun authorFoundAfter(millis: Long): UserProfile {
        delay(millis)
        return author
    }

    // Uploads with [uploading], and records the URLs of the photos deleted again.
    private class FakePostPhoto(private val uploading: suspend () -> String) : PostPhoto {
        val deletedPhotoUrls = mutableListOf<String>()

        override suspend fun upload(): String = uploading()

        override suspend fun delete(photoUrl: String) {
            deletedPhotoUrls += photoUrl
        }
    }
}
