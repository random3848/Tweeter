package edu.csuci.tweeter.location

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds where a photo was taken, so the post screen can place its pin before the user adjusts it.
 *
 * Before calling [locate], request `ACCESS_MEDIA_LOCATION` for imported photos and
 * `ACCESS_FINE_LOCATION` for photos taken in the app. Without them it still works, but finds less:
 * see [ExifLocationReader] and [DeviceLocationProvider].
 *
 * @param context Any context; only its application context is kept.
 */
class PhotoLocator(context: Context) {
    private val exifReader = ExifLocationReader(context)
    private val deviceLocationProvider = DeviceLocationProvider(context)

    /**
     * Returns the best automatic location for the photo at [photoUri], or null when the user has
     * to place a pin. See [LocationChooser.chooseBest] for which source wins.
     *
     * @param capturedInApp Whether the photo was just taken with the app's camera, which makes
     *     the device's current location a good guess.
     */
    suspend fun locate(photoUri: Uri, capturedInApp: Boolean): LocationFix? {
        val exif = withContext(Dispatchers.IO) { exifReader.read(photoUri) }
        val device = if (capturedInApp) deviceLocationProvider.currentFix() else null
        return LocationChooser.chooseBest(exif, device, manual = null, capturedInApp)
    }
}
