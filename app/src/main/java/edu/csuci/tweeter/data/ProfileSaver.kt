package edu.csuci.tweeter.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Saves profiles, and deletes the profile photo each save leaves unused: the photo it replaced
 * or removed once the server has the profile, or its new photo if the server rejects it.
 *
 * It waits for the server in [scope], which outlives the screens, so that the photo is deleted
 * even when the screen that saved has stopped waiting and closed, as long as the app runs.
 */
class ProfileSaver(
    private val profileRepository: ProfileRepository,
    private val photoRepository: PhotoRepository,
    private val scope: CoroutineScope
) {
    /**
     * Starts saving [profile] in place of the stored profile, whose photo is [previousPhotoUrl],
     * and returns the save. It completes once the server has the profile, or fails with
     * [ProfileSaveException] if it can't be saved, and goes on when the caller stops waiting.
     */
    fun save(profile: UserProfile, previousPhotoUrl: String): Deferred<Unit> =
        scope.async { saveDeletingUnusedPhoto(profile, previousPhotoUrl) }

    private suspend fun saveDeletingUnusedPhoto(profile: UserProfile, previousPhotoUrl: String) {
        saveOrDeleteNewPhoto(profile, previousPhotoUrl)
        deleteUnlessKept(previousPhotoUrl, keptPhotoUrl = profile.photoUrl)
    }

    // Saves [profile]. If it can't, the photo it has instead of [previousPhotoUrl] is unused.
    private suspend fun saveOrDeleteNewPhoto(profile: UserProfile, previousPhotoUrl: String) {
        try {
            profileRepository.saveProfile(profile)
        } catch (error: ProfileSaveException) {
            deleteUnlessKept(profile.photoUrl, keptPhotoUrl = previousPhotoUrl)
            throw error
        }
    }

    // Deletes the photo at [photoUrl], unless there is none or the stored profile keeps it as
    // [keptPhotoUrl].
    private suspend fun deleteUnlessKept(photoUrl: String, keptPhotoUrl: String) {
        val isUnused = photoUrl.isNotEmpty() && photoUrl != keptPhotoUrl
        if (isUnused) {
            photoRepository.deleteUnusedPhoto(photoUrl)
        }
    }
}
