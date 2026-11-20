package edu.csuci.tweeter.ui.post

import edu.csuci.tweeter.data.CameraFile

/** How far the in-app camera has got taking a picture for the post. */
sealed interface CaptureStatus {
    /** Not taking a picture. */
    data object Idle : CaptureStatus

    /** Creating the file the picture goes into, off the main thread. */
    data object CreatingFile : CaptureStatus

    /**
     * Waiting for the camera screen to take the picture and say how it went.
     *
     * @property file The file the picture goes into.
     */
    data class Capturing(val file: CameraFile) : CaptureStatus

    /** The last picture couldn't be taken. */
    data object Failed : CaptureStatus
}
