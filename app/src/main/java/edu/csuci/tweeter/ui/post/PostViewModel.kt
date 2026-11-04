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
import edu.csuci.tweeter.data.CameraFiles
import edu.csuci.tweeter.data.PhotoRepository
import edu.csuci.tweeter.data.PhotoUploadException
import edu.csuci.tweeter.data.Post
import edu.csuci.tweeter.data.PostRepository
import edu.csuci.tweeter.data.ProfileRepository
import edu.csuci.tweeter.data.UserProfile
import edu.csuci.tweeter.data.deleteUnusedPhoto
import edu.csuci.tweeter.data.getLoggedInProfile
import edu.csuci.tweeter.location.DeviceLocationProvider
import edu.csuci.tweeter.location.ExifLocationReader
import edu.csuci.tweeter.location.GeoPoint
import edu.csuci.tweeter.location.LocationFix
import edu.csuci.tweeter.location.LocationSource
import edu.csuci.tweeter.location.LocationTagRemover
import edu.csuci.tweeter.location.PhotoLocator
import edu.csuci.tweeter.ui.camera.CameraCapture
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val PHOTO_URI_KEY = "photoUri"
private const val PHOTO_PATH_KEY = "photoPath"

/**
 * Holds the post's photo, taken with the camera or chosen from the camera roll, finds where it
 * was taken, uploads it, shares it as a post, and holds the state the post screen displays.
 *
 * @param cameraFiles Deletes the files of camera photos once nothing needs them.
 * @param cameraCapture Takes pictures with the camera app.
 */
class PostViewModel(
    private val photoRepository: PhotoRepository,
    private val postRepository: PostRepository,
    private val profileRepository: ProfileRepository,
    private val accountRepository: AccountRepository,
    private val photoLocator: PhotoLocator<Uri>,
    private val locationTagRemover: LocationTagRemover,
    private val cameraFiles: CameraFiles,
    private val cameraCapture: CameraCapture,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private var locateJob: Job? = null

    /** The logged-in user's ID, or null while nobody is logged in and posting isn't allowed. */
    val loggedInUid: StateFlow<String?> = accountRepository.loggedInUid

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
     * The URI of the photo to share: a `content://` URI on the phone, or null until the user
     * takes or chooses one, and again once it is shared.
     */
    var photoUri by mutableStateOf(savedStateHandle.get<String>(PHOTO_URI_KEY))
        private set

    // The app's own file holding a camera photo in [photoUri], deleted once nothing needs it.
    private var photoPath by mutableStateOf(savedStateHandle.get<String>(PHOTO_PATH_KEY))

    /** Whether the photo was just taken with the camera, not chosen from the camera roll. */
    val isPhotoFromCamera: Boolean
        get() = photoPath != null

    /** The URI the screen opens the camera app with, as [CameraCapture.uriToOpen] says. */
    val cameraUriToOpen: String?
        get() = cameraCapture.uriToOpen

    /** Whether a picture is being taken, from [takePicture] until the camera app answers. */
    val isTakingPicture: Boolean
        get() = cameraCapture.isTakingPicture

    /** Whether the latest picture couldn't be taken. */
    val cameraFailed: Boolean
        get() = cameraCapture.failed

    /** Starts taking a picture: the screen then opens the camera app with [cameraUriToOpen]. */
    fun takePicture() {
        viewModelScope.launch { cameraCapture.start() }
    }

    /** Stops asking to open the camera app, which the screen has opened. */
    fun cameraOpened() {
        cameraCapture.cameraOpened()
    }

    /**
     * Makes the picture the camera app took the post's photo when [isSaved], and otherwise
     * deletes its file.
     */
    fun pictureTaken(isSaved: Boolean) {
        if (isSaved) {
            cameraCapture.keep()?.let { picture -> replacePhoto(picture.contentUri, picture.path) }
        } else {
            cameraCapture.discard()
        }
    }

    /** Gives up on the picture, since no camera app could open, and reports [cameraFailed]. */
    fun cameraUnavailable() {
        cameraCapture.cameraUnavailable()
    }

    /** Stops reporting [cameraFailed], once the user moves on to another way to get a photo. */
    fun dismissCameraFailure() {
        cameraCapture.dismissFailure()
    }

    /** Makes the photo at [uri], chosen from the camera roll, the post's photo. */
    fun choosePhoto(uri: String) {
        replacePhoto(uri, cameraFilePath = null)
    }

    // Replaces the photo, deleting the previous camera photo's file, and clears the result of
    // sharing the previous one.
    private fun replacePhoto(uri: String, cameraFilePath: String?) {
        clearUploadResult()
        val replacedPath = photoPath
        updatePhoto(uri, cameraFilePath)
        replacedPath?.let(cameraFiles::discard)
    }

    private fun updatePhoto(uri: String?, cameraFilePath: String?) {
        photoUri = uri
        photoPath = cameraFilePath
        savedStateHandle[PHOTO_URI_KEY] = uri
        savedStateHandle[PHOTO_PATH_KEY] = cameraFilePath
    }

    /**
     * Uploads [uri] without its location tags and shares it as a post at [location] by the
     * logged-in user. Ignored while one runs, after the post is shared, or while [location] is
     * null. Fails if nobody is logged in.
     */
    fun uploadPhoto(uri: Uri, caption: String, animalType: String, confidence: Int?) {
        val point = location?.point
        if (isUploading || uploadedPhotoUrl != null || point == null) {
            return
        }

        isUploading = true
        uploadFailed = false
        uploadedPhotoUrl = null

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
                uploadedPhotoUrl = photoUrl
                savedStateHandle["uploadedPhotoUrl"] = uploadedPhotoUrl
                forgetSharedPhoto()
            } catch (error: NobodyLoggedInException) {
                Log.e("PostViewModel", "Nobody is logged in, so the post can't be shared", error)
                uploadFailed = true
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
                isUploading = false
            }
        }
    }

    // The photo at [uri], uploaded as a copy without its location tags.
    private inner class LocationFreePhoto(private val uri: Uri) : PostPhoto {
        // Deletes the copy once the upload is over.
        override suspend fun upload(): String {
            val copy = locationTagRemover.copyWithoutLocation(uri)
            try {
                return photoRepository.uploadPhoto(Uri.fromFile(copy))
            } finally {
                locationTagRemover.deleteCopy(copy)
            }
        }

        override suspend fun delete(photoUrl: String) {
            photoRepository.deleteUnusedPhoto(photoUrl)
        }
    }

    // Clears the previous upload result when a new photo is selected.
    private fun clearUploadResult() {
        if (isUploading) {
            return
        }

        uploadedPhotoUrl = null
        savedStateHandle["uploadedPhotoUrl"] = null
        uploadFailed = false
    }

    // Forgets the photo once it's shared, deleting its file if it was a camera photo.
    private fun forgetSharedPhoto() {
        val sharedPath = photoPath
        updatePhoto(uri = null, cameraFilePath = null)
        sharedPath?.let(cameraFiles::discard)
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
                val cameraFiles = application.container.cameraFiles
                val savedStateHandle = createSavedStateHandle()
                PostViewModel(
                    photoRepository = application.container.photoRepository,
                    postRepository = application.container.postRepository,
                    profileRepository = application.container.profileRepository,
                    accountRepository = application.container.accountRepository,
                    photoLocator = PhotoLocator(
                        readExif = ExifLocationReader(application)::read,
                        currentDeviceFix = DeviceLocationProvider(application)::currentFix,
                        ioDispatcher = Dispatchers.IO
                    ),
                    locationTagRemover = LocationTagRemover(
                        context = application,
                        ioDispatcher = Dispatchers.IO
                    ),
                    cameraFiles = cameraFiles,
                    cameraCapture = CameraCapture(cameraFiles, savedStateHandle),
                    savedStateHandle = savedStateHandle
                )
            }
        }
    }
}
