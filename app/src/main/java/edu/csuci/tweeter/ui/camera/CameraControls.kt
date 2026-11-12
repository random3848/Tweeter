package edu.csuci.tweeter.ui.camera

/**
 * What a screen reads and calls to take a picture with the camera app, through [CameraOpener].
 *
 * [CameraCapture] implements it, and a ViewModel that takes pictures exposes its capture as this
 * interface. A picture the camera app saves becomes the ViewModel's [ChosenPhoto].
 */
interface CameraControls {
    /**
     * The URI of the file for the camera app to save the picture into, or null unless the screen
     * should open the camera app now. The screen calls [cameraOpened] once it has.
     */
    val cameraUriToOpen: String?

    /**
     * Whether the camera app is being opened: from [takePicture] until the screen has opened it
     * or couldn't, or has closed first. Waiting for the camera app's answer doesn't count, since
     * the answer can be lost, such as when Android blocks opening the camera app from the
     * background.
     */
    val isOpeningCamera: Boolean

    /**
     * Whether the latest picture couldn't be taken, because its file couldn't be created or no
     * camera app could open.
     */
    val cameraFailed: Boolean

    /**
     * Starts taking a picture: creates its file, then asks to open the camera app with
     * [cameraUriToOpen]. Ignored while [isOpeningCamera]. It replaces a picture the camera app
     * was opened for but never answered.
     */
    fun takePicture()

    /** Stops asking to open the camera app, which the screen has opened. */
    fun cameraOpened()

    /** Gives up on the picture, since no camera app could open, and reports [cameraFailed]. */
    fun cameraUnavailable()

    /**
     * Gives up on a picture the camera app hasn't been opened for yet, since the screen that
     * would open it has closed: otherwise it would open the next time the screen is shown.
     */
    fun stopOpeningCamera()

    /** Makes the picture the camera app saved the ViewModel's photo. */
    fun pictureSaved()

    /** Gives up on the picture, which the camera app closed without saving. */
    fun pictureCancelled()

    /** Stops reporting [cameraFailed], once the user moves on. */
    fun dismissCameraFailure()
}
