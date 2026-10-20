package edu.csuci.tweeter.location

/** Picks the best location for a photo from the fixes available. */
object LocationChooser {
    /** Device fixes with an accuracy radius at or below this are trusted over EXIF. */
    const val MAX_TRUSTED_DEVICE_ACCURACY_METERS = 50f

    /**
     * Chooses a location for a photo, or returns null when the user has to place a pin.
     *
     * A manual pin always wins. For a photo taken in the app, an accurate device fix comes first,
     * then EXIF, then any device fix. An imported photo uses only EXIF, since where the device is
     * now says nothing about where the photo was taken.
     *
     * @param exif The location in the photo's EXIF metadata, if any.
     * @param device The live device fix, if any.
     * @param manual The pin the user placed, if any.
     * @param capturedInApp Whether the photo was just taken with the app's camera.
     */
    fun chooseBest(
        exif: LocationFix?,
        device: LocationFix?,
        manual: LocationFix?,
        capturedInApp: Boolean
    ): LocationFix? {
        if (manual != null) {
            return manual
        }
        if (!capturedInApp) {
            return exif
        }
        val deviceAccuracy = device?.accuracyMeters
        if (deviceAccuracy != null && deviceAccuracy <= MAX_TRUSTED_DEVICE_ACCURACY_METERS) {
            return device
        }
        return exif ?: device
    }
}
