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
import kotlinx.coroutines.launch

/** Uploads photos and holds the upload state displayed by the post screen. */
class PostViewModel(
    private val photoRepository: PhotoRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
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

    /** Uploads [uri], ignoring duplicate requests while an upload is running. */
    fun uploadPhoto(uri: Uri) {
        if (isUploading || uploadedPhotoUrl != null) {
            return
        }

        isUploading = true
        uploadFailed = false
        uploadedPhotoUrl = null

        viewModelScope.launch {
            try {
                uploadedPhotoUrl = photoRepository.uploadPhoto(uri)
                savedStateHandle["uploadedPhotoUrl"] = uploadedPhotoUrl
            } catch (error: PhotoUploadException) {
                Log.e("PostViewModel", "Photo upload failed", error)
                uploadFailed = true
            } finally {
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

    /** Creates ViewModels using the app's shared photo repository. */
    companion object {
        /** Supplies the photo repository from the application container. */
        val factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                PostViewModel(
                    photoRepository = application.container.photoRepository,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
