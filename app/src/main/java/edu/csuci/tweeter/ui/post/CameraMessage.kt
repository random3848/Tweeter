package edu.csuci.tweeter.ui.post

/** What the camera screen tells the user about the last thing they did on it. */
enum class CameraMessage {
    /** The post was shared. */
    POST_SHARED,

    /** The in-app camera couldn't take the picture. */
    CAPTURE_FAILED,

    /** The camera roll couldn't be opened. */
    CAMERA_ROLL_FAILED
}
