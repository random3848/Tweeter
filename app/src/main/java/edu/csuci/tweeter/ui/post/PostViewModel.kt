package edu.csuci.tweeter.ui.post

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.FirebaseException
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.Avatar
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PhotoUploadException
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.location.GeoPoint
import edu.csuci.tweeter.location.LocationFix
import edu.csuci.tweeter.location.LocationSource
import edu.csuci.tweeter.location.LocationTagRemover
import edu.csuci.tweeter.location.PhotoLocator
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Who every post is by until users have accounts.
private const val GUEST_USERNAME = "guest"

/**
 * Finds where the post's photo was taken, uploads the photo, shares it as a post, and holds the
 * state the post screen displays.
 */
class PostViewModel(
    private val photoRepository: PhotoRepository,
    private val postRepository: PostRepository,
    private val photoLocator: PhotoLocator,
    private val locationTagRemover: LocationTagRemover,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private var locateJob: Job? = null

    /** Whether the photo is being uploaded and shared. */
    var isUploading by mutableStateOf(false)
        private set

    /** The shared photo's URL, or null until the post has been shared. */
    var uploadedPhotoUrl by mutableStateOf(
        savedStateHandle.get<String>("uploadedPhotoUrl")
    )
        private set

    /** Whether the latest attempt to share the post failed. */
    var uploadFailed by mutableStateOf(false)
        private set

    /**
     * Where the post's photo was taken: found by [locatePhoto], or picked by the user with
     * [pickLocation]. Null until either happens, or when no location could be found; the post
     * can't be shared until it is set.
     */
    var location by mutableStateOf<LocationFix?>(null)
        private set

    /** Whether [locatePhoto] is still looking for the photo's location. */
    var isLocating by mutableStateOf(false)
        private set

    /**
     * Uploads [uri] without its location tags and shares it as a post at [location]. Ignored
     * while one runs, after the post is shared, or while [location] is null.
     */
    fun uploadPhoto(uri: Uri) {
        val point = location?.point
        if (isUploading || uploadedPhotoUrl != null || point == null) {
            return
        }

        isUploading = true
        uploadFailed = false
        uploadedPhotoUrl = null

        viewModelScope.launch {
            var copy: File? = null
            try {
                copy = locationTagRemover.copyWithoutLocation(uri)
                val photoUrl = photoRepository.uploadPhoto(Uri.fromFile(copy))
                postRepository.addPost(newPost(photoUrl, point))
                uploadedPhotoUrl = photoUrl
                savedStateHandle["uploadedPhotoUrl"] = uploadedPhotoUrl
            } catch (error: PhotoUploadException) {
                Log.e("PostViewModel", "Photo upload failed", error)
                uploadFailed = true
            } catch (error: FirebaseException) {
                Log.e("PostViewModel", "Could not save the post", error)
                uploadFailed = true
            } catch (error: IOException) {
                Log.e("PostViewModel", "Could not read photo for upload", error)
                uploadFailed = true
            } finally {
                copy?.delete()
                isUploading = false
            }
        }
    }

    /** Clears the previous upload result when a new photo is selected. */
    fun clearUploadResult() {
        if (isUploading) {
            return
        }

        uploadedPhotoUrl = null
        savedStateHandle["uploadedPhotoUrl"] = null
        uploadFailed = false
    }

    /**
     * Forgets the previous photo's location and looks for where the photo at [uri] was taken.
     *
     * @param capturedInApp Whether the photo was just taken with the camera, which makes the
     *     device's current location a good guess.
     */
    fun locatePhoto(uri: Uri, capturedInApp: Boolean) {
        locateJob?.cancel()
        location = null
        isLocating = true
        locateJob = viewModelScope.launch {
            location = photoLocator.locate(uri, capturedInApp)
            isLocating = false
        }
    }

    /** Sets the post's location to [point], which the user picked on the map. */
    fun pickLocation(point: GeoPoint) {
        locateJob?.cancel()
        isLocating = false
        location = LocationFix(point, LocationSource.MANUAL)
    }

    private fun newPost(photoUrl: String, point: GeoPoint): Post = Post(
        id = UUID.randomUUID().toString(),
        username = GUEST_USERNAME,
        avatar = Avatar(initials = GUEST_USERNAME.take(1).uppercase()),
        locationName = "",
        latitude = point.latitude,
        longitude = point.longitude,
        imageUri = photoUrl,
        caption = "",
        likeCount = 0,
        commentCount = 0,
        isLiked = false,
        isBookmarked = false,
        // Firestore replaces this with the time it receives the post.
        timestamp = Instant.now()
    )

    /** Creates ViewModels using the app's shared repositories. */
    companion object {
        /** Supplies the photo and post repositories from the application container. */
        val factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                PostViewModel(
                    photoRepository = application.container.photoRepository,
                    postRepository = application.container.postRepository,
                    photoLocator = PhotoLocator(application),
                    locationTagRemover = LocationTagRemover(application),
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
