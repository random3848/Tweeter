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
import edu.csuci.tweeter.data.AccountRepository
import edu.csuci.tweeter.data.CameraFile
import edu.csuci.tweeter.data.CameraFileStore
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PhotoUploadException
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import edu.csuci.tweeter.data.deleteUnusedPhoto
import edu.csuci.tweeter.data.getLoggedInProfile
import edu.csuci.tweeter.location.ExifLocationReader
import edu.csuci.tweeter.location.GeoPoint
import edu.csuci.tweeter.location.LocationFix
import edu.csuci.tweeter.location.LocationSource
import edu.csuci.tweeter.location.PhotoLocator
import edu.csuci.tweeter.ui.camera.ChosenPhoto
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val UPLOADED_PHOTO_URL_KEY = "uploadedPhotoUrl"

/**
 * Holds the post's photo, taken with the camera or chosen from the camera roll, finds where it
 * was taken, uploads it, shares it as a post, and holds the state the post screen displays.
 *
 * @param photo The post's photo. It isn't replaced while it uploads.
 */
class PostViewModel(
    private val photoRepository: PhotoRepository,
    private val postRepository: PostRepository,
    private val profileRepository: ProfileRepository,
    private val accountRepository: AccountRepository,
    private val photoLocator: PhotoLocator<String>,
    private val photo: ChosenPhoto,
    private val cameraFileStore: CameraFileStore,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private var locateJob: Job? = null

    // The photo whose latest upload failed, or null.
    private var failedPhotoUri by mutableStateOf<String?>(null)

    // The photo the screen asked location permissions for last, so that it asks once per photo.
    private var photoAskedToLocate by mutableStateOf<String?>(null)

    /** The logged-in user's ID, or null while nobody is logged in and posting isn't allowed. */
    val loggedInUid: StateFlow<String?> = accountRepository.loggedInUid

    /** Whether the photo is being uploaded and shared. */
    var isUploading by mutableStateOf(false)
        private set

    /** The URL of the photo of the post shared last, or null until a post has been shared. */
    var uploadedPhotoUrl by mutableStateOf(savedStateHandle.get<String>(UPLOADED_PHOTO_URL_KEY))
        private set

    /** Whether sharing the photo at [photoUri] failed the last time it was tried. */
    val uploadFailed: Boolean
        get() = failedPhotoUri != null && failedPhotoUri == photo.uri

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

    /** How far the in-app camera has got taking a picture for the post. */
    var captureStatus by mutableStateOf<CaptureStatus>(CaptureStatus.Idle)
        private set

    /** Whether the in-app camera can take a picture now: none is being taken or shared. */
    val canTakePicture: Boolean
        get() = !isCapturing && !isUploading

    private val isCapturing: Boolean
        get() = captureStatus == CaptureStatus.CreatingFile ||
            captureStatus is CaptureStatus.Capturing

    /**
     * The URI of the photo to share: a `content://` URI on the phone, or null until the user
     * takes or chooses one, and again once it is shared.
     */
    val photoUri: String?
        get() = photo.uri

    /** Whether the photo was just taken with the camera, not chosen from the camera roll. */
    val isPhotoFromCamera: Boolean
        get() = photo.isFromCamera

    /**
     * The photo whose location the screen should find next, or null: a new photo, until the
     * screen calls [locationPermissionsRequested]. Finding it takes permissions the screen asks
     * for first; whatever the user answers, it then calls [locatePhoto].
     */
    val photoToLocate: String?
        get() = photo.uri.takeIf { it != photoAskedToLocate }

    /**
     * Makes the photo at [uri], chosen from the camera roll, the post's photo. Ignored while it
     * uploads.
     */
    fun choosePhoto(uri: String) {
        clearMessages()
        photo.chooseFromCameraRoll(uri)
    }

    /** Clears status banners from an earlier capture or upload. */
    fun clearMessages() {
        if (captureStatus == CaptureStatus.Failed) {
            captureStatus = CaptureStatus.Idle
        }
        clearUploadedPhotoUrl()
    }

    /**
     * Prepares a temporary file off the main thread for the in-app camera to save a picture into.
     * Ignored while a capture or upload is already running.
     */
    fun requestPhotoCapture() {
        if (!canTakePicture) {
            return
        }

        clearMessages()
        captureStatus = CaptureStatus.CreatingFile
        viewModelScope.launch {
            try {
                captureFileCreated(cameraFileStore.create())
            } catch (error: IOException) {
                Log.e("PostViewModel", "Could not create camera photo file", error)
                captureFileNotCreated()
            }
        }
    }

    // Hands [file] to the camera screen to take the picture into, or deletes it if the capture
    // was cancelled meanwhile.
    private fun captureFileCreated(file: CameraFile) {
        if (captureStatus == CaptureStatus.CreatingFile) {
            captureStatus = CaptureStatus.Capturing(file)
        } else {
            cameraFileStore.discard(file.path)
        }
    }

    private fun captureFileNotCreated() {
        if (captureStatus == CaptureStatus.CreatingFile) {
            captureStatus = CaptureStatus.Failed
        }
    }

    /**
     * Makes [file], which the in-app camera saved a picture into, the post's photo. It's deleted
     * instead while the photo uploads, and when it's not the file of the running capture, which
     * was cancelled.
     */
    fun onPhotoCaptured(file: CameraFile) {
        if (captureStatus != CaptureStatus.Capturing(file)) {
            cameraFileStore.discard(file.path)
            return
        }

        captureStatus = CaptureStatus.Idle
        photo.chooseCameraPicture(file)
    }

    /**
     * Marks the in-app capture into [file] as failed, unless it was cancelled, and deletes the
     * unused file.
     */
    fun onCaptureFailed(file: CameraFile) {
        if (captureStatus == CaptureStatus.Capturing(file)) {
            captureStatus = CaptureStatus.Failed
        }
        cameraFileStore.discard(file.path)
    }

    /**
     * Cancels the in-app capture, for when the camera screen that would take it leaves: deletes
     * its file, or the file still being created once it is, so that the screen shown next doesn't
     * take a picture nobody asked it for. A picture saved into the file afterwards is deleted.
     */
    fun cancelPhotoCapture() {
        (captureStatus as? CaptureStatus.Capturing)?.let { cameraFileStore.discard(it.file.path) }
        if (isCapturing) {
            captureStatus = CaptureStatus.Idle
        }
    }

    /** Forgets the photo, to take another. Ignored while it uploads. */
    fun retakePhoto() {
        if (isUploading) {
            return
        }

        locateJob?.cancel()
        location = null
        isLocating = false
        clearMessages()
        photo.clear()
        photoAskedToLocate = null
    }

    override fun onCleared() {
        cancelPhotoCapture()
        super.onCleared()
    }

    private fun clearUploadedPhotoUrl() {
        uploadedPhotoUrl = null
        savedStateHandle[UPLOADED_PHOTO_URL_KEY] = null
    }

    /** Stops asking to find [photoToLocate], whose permissions the screen has asked for. */
    fun locationPermissionsRequested() {
        photoAskedToLocate = photo.uri
    }

    /**
     * Forgets the previous location and looks for where the photo at [photoUri] was taken: where
     * the device is for a photo just taken with the camera, or what the photo's own GPS tags
     * say. Ignored while there's no photo.
     */
    fun locatePhoto() {
        val uri = photo.uri ?: return
        val capturedInApp = photo.isFromCamera
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

    /**
     * Uploads the photo at [photoUri] without its location tags and shares it as a post at
     * [location] by the logged-in user, then forgets the photo. Ignored while one runs, or while
     * [photoUri] or [location] is null. Fails if nobody is logged in.
     */
    fun uploadPhoto(caption: String, animalType: String, confidence: Int?) {
        val uri = photo.uri
        val point = location?.point
        if (isUploading || uri == null || point == null) {
            return
        }

        isUploading = true
        failedPhotoUri = null
        photo.lock()

        viewModelScope.launch {
            try {
                val photoUrl = sharePost(
                    photo = LocationFreePhoto(uri),
                    findAuthor = {
                        profileRepository.getLoggedInProfile(accountRepository).first()
                    },
                    addPost = { uploadedUrl, author ->
                        postRepository.addPost(
                            newPost(uploadedUrl, point, author, caption, animalType, confidence)
                        )
                    }
                )
                forgetSharedPhoto(photoUrl)
            } catch (error: NobodyLoggedInException) {
                Log.e("PostViewModel", "Nobody is logged in, so the post can't be shared", error)
                failedPhotoUri = uri
            } catch (error: PhotoUploadException) {
                Log.e("PostViewModel", "Photo upload failed", error)
                failedPhotoUri = uri
            } catch (error: FirebaseException) {
                Log.e("PostViewModel", "Could not save the post", error)
                failedPhotoUri = uri
            } finally {
                isUploading = false
                photo.unlock()
            }
        }
    }

    // The photo at [photoUri], which the repository uploads without its location tags.
    private inner class LocationFreePhoto(private val photoUri: String) : PostPhoto {
        override suspend fun upload(): String = photoRepository.uploadPhoto(photoUri)

        override suspend fun delete(photoUrl: String) {
            photoRepository.deleteUnusedPhoto(photoUrl)
        }
    }

    // Forgets the photo, shared as [photoUrl], and its location. The photo chosen next is located
    // afresh, even if it's the same one.
    private fun forgetSharedPhoto(photoUrl: String) {
        uploadedPhotoUrl = photoUrl
        savedStateHandle[UPLOADED_PHOTO_URL_KEY] = photoUrl
        location = null
        photo.clear()
        photoAskedToLocate = null
    }

    private fun newPost(
        photoUrl: String,
        point: GeoPoint,
        author: UserProfile,
        caption: String,
        animalType: String,
        confidence: Int?
    ): Post = Post(
        id = UUID.randomUUID().toString(),
        authorUid = author.uid,
        username = author.displayName,
        avatar = author.avatar,
        locationName = "",
        latitude = point.latitude,
        longitude = point.longitude,
        imageUri = photoUrl,
        caption = caption,
        likeCount = 0,
        commentCount = 0,
        isLiked = false,
        isBookmarked = false,
        // Firestore replaces this with the time it receives the post.
        timestamp = Instant.now(),
        animalType = animalType,
        confidence = confidence
    )

    /** Creates ViewModels using the app's shared repositories. */
    companion object {
        /** Supplies the repositories and helpers it needs from the application container. */
        val factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as TweeterApplication
                val cameraFileStore = application.container.cameraFileStore
                val savedStateHandle = createSavedStateHandle()
                val photo = ChosenPhoto(cameraFileStore, savedStateHandle)
                PostViewModel(
                    photoRepository = application.container.photoRepository,
                    postRepository = application.container.postRepository,
                    profileRepository = application.container.profileRepository,
                    accountRepository = application.container.accountRepository,
                    photoLocator = PhotoLocator(
                        readExif = { ExifLocationReader(application).read(Uri.parse(it)) },
                        currentDeviceFix =
                            application.container.deviceLocationProvider::getAndSaveCurrentFix,
                        ioDispatcher = Dispatchers.IO
                    ),
                    photo = photo,
                    cameraFileStore = cameraFileStore,
                    savedStateHandle = savedStateHandle
                )
            }
        }
    }
}
