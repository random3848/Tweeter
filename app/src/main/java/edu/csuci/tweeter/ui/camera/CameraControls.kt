package edu.csuci.tweeter.ui.camera

/**
 * What a screen reads and calls to take a picture with the camera app, through [CameraOpener].
 *
 * [CameraCapture] implements it, and a ViewModel that takes pictures exposes its capture as this
 * interface. What happens to a picture the camera app saved is up to the ViewModel.
 */
interface CameraControls {
    /**
     * The URI of the file for the camera app to save the picture into, or null unless the screen
     * should open the camera app now. The screen calls [cameraOpened] once it has.
     */
    val cameraUriToOpen: String?

    /** Whether a picture is being taken: from [takePicture] until the camera app answers. */
    val isTakingPicture: Boolean

    /**
     * Whether the latest picture couldn't be taken, because its file couldn't be created or no
     * camera app could open.
     */
    val cameraFailed: Boolean

    /**
     * Starts taking a picture: creates its file, then asks to open the camera app with
     * [cameraUriToOpen]. Ignored while a picture is being taken.
     */
    fun takePicture()

    /** Stops asking to open the camera app, which the screen has opened. */
    fun cameraOpened()

    /** Gives up on the picture, since no camera app could open, and reports [cameraFailed]. */
    fun cameraUnavailable()

    /** Stops reporting [cameraFailed], once the user moves on. */
    fun dismissCameraFailure()
}
