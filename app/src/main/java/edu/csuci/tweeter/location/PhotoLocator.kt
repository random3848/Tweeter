package edu.csuci.tweeter.location

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Finds where a photo was taken, so the post screen can place its pin before the user adjusts it.
 *
 * Before calling [locate], request `ACCESS_MEDIA_LOCATION` for imported photos and
 * `ACCESS_FINE_LOCATION` for photos taken in the app. Without them it still works, but finds less:
 * see [ExifLocationReader] and [DeviceLocationProvider].
 *
 * @param PhotoT What identifies a photo to [readExif]; the app uses its content `Uri`.
 * @param readExif Reads the location in a photo's EXIF metadata, blocking while it does
 *     ([ExifLocationReader.read] in the app).
 * @param currentDeviceFix Waits for the device's current location
 *     ([DeviceLocationProvider.currentFix] in the app).
 * @param ioDispatcher Where [readExif] runs, since it blocks on file I/O.
 */
class PhotoLocator<PhotoT>(
    private val readExif: suspend (PhotoT) -> LocationFix?,
    private val currentDeviceFix: suspend () -> LocationFix?,
    private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Returns the best automatic location for [photo], or null when the user has to place a pin.
     * See [LocationChooser.chooseBest] for which source wins. The photo's EXIF metadata is read
     * while the device looks for a fix, so this takes as long as the slower of the two.
     *
     * @param capturedInApp Whether the photo was just taken with the app's camera, which makes
     *     the device's current location a good guess. Only then is the device asked.
     */
    suspend fun locate(photo: PhotoT, capturedInApp: Boolean): LocationFix? = coroutineScope {
        val exif = async(ioDispatcher) { readExif(photo) }
        val device = if (capturedInApp) currentDeviceFix() else null
        LocationChooser.chooseBest(exif.await(), device, manual = null, capturedInApp)
    }
}
