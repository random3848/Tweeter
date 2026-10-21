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
import edu.csuci.tweeter.TweeterApplication
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PhotoUploadException
import edu.csuci.tweeter.location.GeoPoint
import edu.csuci.tweeter.location.LocationFix
import edu.csuci.tweeter.location.LocationSource
import edu.csuci.tweeter.location.LocationTagRemover
import edu.csuci.tweeter.location.PhotoLocator
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Finds where the post's photo was taken, uploads the photo, and holds the state the post screen
 * displays.
 */
class PostViewModel(
    private val photoRepository: PhotoRepository,
    private val photoLocator: PhotoLocator,
    private val locationTagRemover: LocationTagRemover,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private var locateJob: Job? = null

    /** Whether a photo upload is currently running. */
    var isUploading by mutableStateOf(false)
        private set

    /** The uploaded photo's URL, or null before a successful upload. */
    var uploadedPhotoUrl by mutableStateOf(
        savedStateHandle.get<String>("uploadedPhotoUrl")
    )
        private set

    /** Whether the latest upload attempt failed. */
    var uploadFailed by mutableStateOf(false)
        private set

    /**
     * Where the post's photo was taken: found by [locatePhoto], or picked by the user with
     * [pickLocation]. Null until either happens, or when no location could be found.
     *
     * Creating the post (#86) reads it: set [edu.csuci.tweeter.data.Post.latitude] and
     * [edu.csuci.tweeter.data.Post.longitude] from its `point`, and don't create the post while
     * it is null; ask the user to tap the map instead. It has no place name, so
     * [edu.csuci.tweeter.data.Post.locationName] still has to come from the user.
     */
    var location by mutableStateOf<LocationFix?>(null)
        private set

    /** Whether [locatePhoto] is still looking for the photo's location. */
    var isLocating by mutableStateOf(false)
        private set

    /** Uploads [uri] without its location tags, ignoring duplicate requests while one runs. */
    fun uploadPhoto(uri: Uri) {
        if (isUploading || uploadedPhotoUrl != null) {
            return
        }

        isUploading = true
        uploadFailed = false
        uploadedPhotoUrl = null

        viewModelScope.launch {
            var copy: File? = null
            try {
                copy = locationTagRemover.copyWithoutLocation(uri)
                uploadedPhotoUrl = photoRepository.uploadPhoto(Uri.fromFile(copy))
                savedStateHandle["uploadedPhotoUrl"] = uploadedPhotoUrl
            } catch (error: PhotoUploadException) {
                Log.e("PostViewModel", "Photo upload failed", error)
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

    /** Creates ViewModels using the app's shared photo repository. */
    companion object {
        /** Supplies the photo repository from the application container. */
        val factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                PostViewModel(
                    photoRepository = application.container.photoRepository,
                    photoLocator = PhotoLocator(application),
                    locationTagRemover = LocationTagRemover(application),
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
