package edu.csuci.tweeter.location

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

/**
 * What was found in a photo's EXIF metadata.
 *
 * @property fix the GPS location, or null if the photo has none.
 * @property altitudeMeters GPS altitude, if tagged.
 * @property processingMethod the GPS processing method tag (e.g. "GPS", "NETWORK"), if tagged.
 * @property dateTimeOriginal the raw EXIF capture time ("yyyy:MM:dd HH:mm:ss", local), if tagged.
 * @property openedOriginal true if the unredacted original was read via
 *   [MediaStore.setRequireOriginal]; false if the plain (possibly location-redacted) URI was used.
 * @property note a short explanation, e.g. why GPS is missing.
 */
data class ExifReport(
    val fix: LocationFix?,
    val altitudeMeters: Double?,
    val processingMethod: String?,
    val dateTimeOriginal: String?,
    val openedOriginal: Boolean,
    val note: String,
)

/** Reads GPS tags from photo EXIF metadata. */
class ExifLocationReader(context: Context) {

    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val appContext = context.applicationContext

    /**
     * Reads the EXIF location from the image at [uri].
     *
     * Android redacts EXIF GPS from media URIs unless the app holds `ACCESS_MEDIA_LOCATION` and
     * asks for the original, so this first tries the original and falls back to the plain URI.
     */
    fun read(uri: Uri): ExifReport {
        val original = openOriginal(uri)
        val stream = original ?: resolver.openInputStream(uri)
            ?: return emptyReport(openedOriginal = false, note = "Could not open image")
        val exif = stream.use { ExifInterface(it) }
        val openedOriginal = original != null

        val latLong = exif.latLong
        val altitude = exif.getAltitude(Double.NaN).takeUnless { it.isNaN() }
        val method = exif.getAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD)
        val taken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        if (latLong == null) {
            val note = if (!openedOriginal && isMediaUri(uri)) {
                "No GPS tags in EXIF (location may have been redacted)"
            } else {
                "No GPS tags in EXIF"
            }
            return ExifReport(null, altitude, method, taken, openedOriginal, note)
        }
        if (latLong[0] == 0.0 && latLong[1] == 0.0) {
            return ExifReport(
                null,
                altitude,
                method,
                taken,
                openedOriginal,
                "GPS tags are 0,0 (redacted or no fix)",
            )
        }
        val fix = LocationFix(
            point = GeoPoint(latLong[0], latLong[1]),
            source = LocationSource.EXIF,
            accuracyMeters = exif.getAttributeDouble(
                ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
                Double.NaN,
            ).takeUnless { it.isNaN() }?.toFloat(),
            timeMillis = exif.gpsDateTime,
        )
        return ExifReport(fix, altitude, method, taken, openedOriginal, "GPS tags found")
    }

    /**
     * Opens the unredacted original of a MediaStore image, or returns null when the URI isn't
     * backed by MediaStore (e.g. Photo Picker URIs) or the permission is missing.
     */
    private fun openOriginal(uri: Uri): InputStream? {
        val mediaUri = when {
            DocumentsContract.isDocumentUri(appContext, uri) ->
                runCatching { MediaStore.getMediaUri(appContext, uri) }.getOrNull()
            uri.authority == MediaStore.AUTHORITY -> uri
            else -> null
        } ?: return null
        return runCatching {
            resolver.openInputStream(MediaStore.setRequireOriginal(mediaUri))
        }.getOrNull()
    }

    /** True for URIs served by MediaStore or the Photo Picker, which may redact location. */
    private fun isMediaUri(uri: Uri): Boolean =
        uri.authority == MediaStore.AUTHORITY || DocumentsContract.isDocumentUri(appContext, uri)

    private fun emptyReport(openedOriginal: Boolean, note: String) =
        ExifReport(null, null, null, null, openedOriginal, note)
}
