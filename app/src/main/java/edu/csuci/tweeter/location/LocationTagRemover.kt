package edu.csuci.tweeter.location

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Where the copies are written, inside the app's cache directory.
private const val COPY_DIRECTORY = "uploads"

// Every EXIF tag that says where a photo was taken, plus XMP, which camera apps can also put a
// location in.
private val LOCATION_TAGS = listOf(
    ExifInterface.TAG_GPS_VERSION_ID,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_SATELLITES,
    ExifInterface.TAG_GPS_STATUS,
    ExifInterface.TAG_GPS_MEASURE_MODE,
    ExifInterface.TAG_GPS_DOP,
    ExifInterface.TAG_GPS_SPEED_REF,
    ExifInterface.TAG_GPS_SPEED,
    ExifInterface.TAG_GPS_TRACK_REF,
    ExifInterface.TAG_GPS_TRACK,
    ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
    ExifInterface.TAG_GPS_IMG_DIRECTION,
    ExifInterface.TAG_GPS_MAP_DATUM,
    ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
    ExifInterface.TAG_GPS_DEST_LATITUDE,
    ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
    ExifInterface.TAG_GPS_DEST_LONGITUDE,
    ExifInterface.TAG_GPS_DEST_BEARING_REF,
    ExifInterface.TAG_GPS_DEST_BEARING,
    ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
    ExifInterface.TAG_GPS_DEST_DISTANCE,
    ExifInterface.TAG_GPS_PROCESSING_METHOD,
    ExifInterface.TAG_GPS_AREA_INFORMATION,
    ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_DIFFERENTIAL,
    ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
    ExifInterface.TAG_XMP
)

/**
 * Makes copies of photos without the tags that say where they were taken.
 *
 * A post's location is the one the user picked, so the uploaded photo mustn't carry another one:
 * anyone with its download URL could read the exact spot from its metadata, even when the user
 * moved the pin away from it.
 *
 * @param context Any context; only its application context is kept.
 */
class LocationTagRemover(context: Context) {
    private val appContext = context.applicationContext

    /**
     * Copies the photo at [uri] into the app's cache and removes the copy's location tags. The
     * caller deletes the copy when it is done with it.
     *
     * @throws IOException If the photo can't be read, or its format can't have tags removed
     *     (ExifInterface writes JPEG, PNG and WebP). No copy is left behind then.
     */
    suspend fun copyWithoutLocation(uri: Uri): File = withContext(Dispatchers.IO) {
        val directory = File(appContext.cacheDir, COPY_DIRECTORY)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Could not create $directory")
        }
        val copy = File.createTempFile("photo_", ".jpg", directory)
        try {
            val input = appContext.contentResolver.openInputStream(uri) ?:
                throw IOException("Could not open $uri")
            input.use { source -> copy.outputStream().use { source.copyTo(it) } }
            val exif = ExifInterface(copy)
            for (tag in LOCATION_TAGS) {
                exif.setAttribute(tag, null)
            }
            exif.saveAttributes()
            copy
        } catch (error: IOException) {
            copy.delete()
            throw error
        } catch (error: UnsupportedOperationException) {
            copy.delete()
            throw IOException("Can't remove the location tags from $uri", error)
        }
    }
}
