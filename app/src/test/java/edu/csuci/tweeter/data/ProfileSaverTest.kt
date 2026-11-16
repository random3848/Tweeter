@file:OptIn(ExperimentalCoroutinesApi::class)

package edu.csuci.tweeter.data

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

private const val UID = "maya"
private const val PREVIOUS_PHOTO_URL = "https://example.com/previous.jpg"
private const val NEW_PHOTO_URL = "https://example.com/new.jpg"

class ProfileSaverTest {
    private val profileRepository = FakeProfileRepository()
    private val photoRepository = FakePhotoRepository()

    @Test
    fun save_newPhoto_savesProfileAndDeletesPreviousPhoto() = runTest {
        val profile = profileWithPhoto(NEW_PHOTO_URL)

        profileSaver().save(profile, PREVIOUS_PHOTO_URL).await()

        assertEquals(profile, profileRepository.getProfile(UID).first())
        assertEquals(listOf(PREVIOUS_PHOTO_URL), photoRepository.deletedPhotoUrls)
    }

    @Test
    fun save_photoRemoved_deletesPreviousPhoto() = runTest {
        profileSaver().save(profileWithPhoto(""), PREVIOUS_PHOTO_URL).await()

        assertEquals(listOf(PREVIOUS_PHOTO_URL), photoRepository.deletedPhotoUrls)
    }

    @Test
    fun save_photoKept_deletesNothing() = runTest {
        profileSaver().save(profileWithPhoto(PREVIOUS_PHOTO_URL), PREVIOUS_PHOTO_URL).await()

        assertEquals(emptyList<String>(), photoRepository.deletedPhotoUrls)
    }

    @Test
    fun save_noPreviousPhoto_deletesNothing() = runTest {
        profileSaver().save(profileWithPhoto(NEW_PHOTO_URL), previousPhotoUrl = "").await()

        assertEquals(emptyList<String>(), photoRepository.deletedPhotoUrls)
    }

    @Test
    fun save_rejected_failsAndDeletesNewPhoto() = runTest {
        profileRepository.failsToSave = true

        val error = runCatching {
            profileSaver().save(profileWithPhoto(NEW_PHOTO_URL), PREVIOUS_PHOTO_URL).await()
        }.exceptionOrNull()

        assertEquals(ProfileSaveException::class, error?.let { it::class })
        assertEquals(listOf(NEW_PHOTO_URL), photoRepository.deletedPhotoUrls)
    }

    @Test
    fun save_rejectedWithPhotoKept_deletesNothing() = runTest {
        profileRepository.failsToSave = true

        runCatching {
            profileSaver().save(profileWithPhoto(PREVIOUS_PHOTO_URL), PREVIOUS_PHOTO_URL).await()
        }

        assertEquals(emptyList<String>(), photoRepository.deletedPhotoUrls)
    }

    @Test
    fun save_rejectedAfterCallerStoppedWaiting_deletesNewPhoto() = runTest {
        val saveFinished = CompletableDeferred<Unit>()
        profileRepository.saveFinished = saveFinished
        profileRepository.failsToSave = true
        profileSaver().save(profileWithPhoto(NEW_PHOTO_URL), PREVIOUS_PHOTO_URL)

        saveFinished.complete(Unit)

        assertEquals(listOf(NEW_PHOTO_URL), photoRepository.deletedPhotoUrls)
    }

    // Saves in a scope of its own, as the app's, which a failed save doesn't cancel.
    private fun TestScope.profileSaver(): ProfileSaver = ProfileSaver(
        profileRepository = profileRepository,
        photoRepository = photoRepository,
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
    )

    private fun profileWithPhoto(photoUrl: String): UserProfile = UserProfile(
        uid = UID,
        displayName = "Maya",
        bio = "",
        photoUrl = photoUrl
    )
}
