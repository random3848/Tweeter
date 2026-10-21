package edu.csuci.tweeter.location

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.io.InputStream

/**
 * Reads where a photo was taken from the GPS tags in its EXIF metadata.
 *
 * Android removes those tags from shared photos unless the app asks for the original file and
 * holds `ACCESS_MEDIA_LOCATION`, a runtime permission the caller must request before the photo
 * is read. The Photo Picker always removes them, so photos to be located this way should come
 * from the Files picker (`ActivityResultContracts.OpenDocument`).
 *
 * @param context Any context; only its application context is kept.
 */
class ExifLocationReader(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Returns the GPS location tagged in the image at [uri], or null if it has none. */
    fun read(uri: Uri): LocationFix? {
        val exif = try {
            val stream = openOriginal(uri) ?: resolver.openInputStream(uri) ?: return null
            stream.use { ExifInterface(it) }
        } catch (e: IOException) {
            return null
        }
        val latLong = exif.latLong ?: return null
        // Some apps write 0,0 when they have no fix or have removed the location.
        if (latLong[0] == 0.0 && latLong[1] == 0.0) {
            return null
        }
        val accuracy = exif.getAttributeDouble(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, -1.0)
        return LocationFix(
            point = GeoPoint(latLong[0], latLong[1]),
            source = LocationSource.EXIF,
            accuracyMeters = accuracy.takeIf { it >= 0 }?.toFloat(),
            timeMillis = exif.gpsDateTime
        )
    }

    /**
     * Opens the unredacted original of a MediaStore image. Returns null when the URI isn't backed
     * by MediaStore or the original can't be opened.
     */
    private fun openOriginal(uri: Uri): InputStream? {
        val mediaUri = when {
            DocumentsContract.isDocumentUri(appContext, uri) -> {
                runCatching { MediaStore.getMediaUri(appContext, uri) }.getOrNull()
            }

            uri.authority == MediaStore.AUTHORITY -> uri

            else -> null
        } ?: return null
        return runCatching {
            resolver.openInputStream(MediaStore.setRequireOriginal(mediaUri))
        }.getOrNull()
    }
}
