package edu.csuci.tweeter

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.csuci.tweeter.location.DeviceLocationProvider
import edu.csuci.tweeter.location.ExifLocationReader
import edu.csuci.tweeter.location.ExifReport
import edu.csuci.tweeter.location.GeoPoint
import edu.csuci.tweeter.location.LocationChoice
import edu.csuci.tweeter.location.LocationChooser
import edu.csuci.tweeter.location.LocationFix
import edu.csuci.tweeter.location.LocationSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How the photo under test got into the app. */
enum class ImportPath {
    /** Taken just now through the camera intent. */
    CAMERA,

    /** Chosen with the system Photo Picker. */
    PHOTO_PICKER,

    /** Chosen with the Storage Access Framework document picker. */
    FILES,
}

/**
 * Everything the location test screen shows.
 *
 * @property photoUri the photo under test, if any.
 * @property importPath how [photoUri] was obtained.
 * @property exif what the photo's EXIF metadata contained.
 * @property device the most recent live device fix.
 * @property deviceStatus a short status line for the device fix.
 * @property manual the pin the user dropped, if any.
 * @property choice the location chosen for the photo.
 * @property busy true while EXIF or the device fix is being read.
 * @property log one line per saved test, newest first.
 */
data class LocationTestUiState(
    val photoUri: Uri? = null,
    val importPath: ImportPath? = null,
    val exif: ExifReport? = null,
    val device: LocationFix? = null,
    val deviceStatus: String = "No device fix yet",
    val manual: LocationFix? = null,
    val choice: LocationChoice? = null,
    val busy: Boolean = false,
    val log: List<String> = emptyList(),
)

/** Runs one photo-location test at a time and logs results to a CSV file. */
class LocationTestViewModel(application: Application) : AndroidViewModel(application) {

    private val exifReader = ExifLocationReader(application)
    private val deviceLocation = DeviceLocationProvider(application)
    private val _uiState = MutableStateFlow(LocationTestUiState())

    /** The current screen state. */
    val uiState: StateFlow<LocationTestUiState> = _uiState.asStateFlow()

    /** The CSV file tests are appended to; pull it with `adb pull`. */
    val csvFile: File = File(application.getExternalFilesDir(null), "location_tests.csv")

    /** Creates an empty file for the camera app to write into and returns its content URI. */
    fun newCaptureUri(): Uri {
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    /** Starts a test for a photo that arrived via [path]. */
    fun onPhoto(uri: Uri, path: ImportPath) {
        _uiState.update {
            it.copy(
                photoUri = uri,
                importPath = path,
                exif = null,
                manual = null,
                choice = null,
                busy = true,
            )
        }
        viewModelScope.launch {
            val exif = withContext(Dispatchers.IO) { exifReader.read(uri) }
            _uiState.update { it.copy(exif = exif) }
            recomputeChoice()
            refreshDeviceFix()
            _uiState.update { it.copy(busy = false) }
        }
    }

    /** Fetches a fresh live device fix and updates the choice. */
    suspend fun refreshDeviceFix() {
        if (!deviceLocation.hasPermission()) {
            _uiState.update { it.copy(deviceStatus = "Location permission not granted") }
            return
        }
        _uiState.update { it.copy(deviceStatus = "Getting device fix…") }
        val fix = deviceLocation.currentFix()
        _uiState.update {
            it.copy(
                device = fix ?: it.device,
                deviceStatus = fix?.let { "Fix received" } ?: "No device fix (is location on?)",
            )
        }
        recomputeChoice()
    }

    /** Launches [refreshDeviceFix] from UI code. */
    fun requestDeviceFix() {
        viewModelScope.launch { refreshDeviceFix() }
    }

    /** Drops or moves the manual pin to [point]. */
    fun onMapTapped(point: GeoPoint) {
        _uiState.update {
            it.copy(
                manual = LocationFix(point, LocationSource.MANUAL, timeMillis = now()),
            )
        }
        recomputeChoice()
    }

    /** Removes the manual pin. */
    fun clearManualPin() {
        _uiState.update { it.copy(manual = null) }
        recomputeChoice()
    }

    /** Appends the current test to the CSV file and the on-screen log. */
    fun saveResult() {
        val state = _uiState.value
        val exif = state.exif?.fix
        val device = state.device
        val manual = state.manual
        val time = now()
        val row = listOf(
            timestampFormat.format(Date(time)),
            state.importPath?.name.orEmpty(),
            state.exif?.openedOriginal?.toString().orEmpty(),
            exif?.point?.latitude.fmt(),
            exif?.point?.longitude.fmt(),
            exif?.accuracyMeters.fmt(),
            device?.point?.latitude.fmt(),
            device?.point?.longitude.fmt(),
            device?.accuracyMeters.fmt(),
            device?.timeMillis?.let { (time - it) / 1000 }?.toString().orEmpty(),
            manual?.point?.latitude.fmt(),
            manual?.point?.longitude.fmt(),
            state.choice?.fix?.source?.name.orEmpty(),
            distance(exif, device).fmt(),
            distance(exif, manual).fmt(),
            distance(device, manual).fmt(),
        ).joinToString(",")
        viewModelScope.launch(Dispatchers.IO) {
            val isNew = !csvFile.exists()
            csvFile.appendText(if (isNew) "$CSV_HEADER\n$row\n" else "$row\n")
        }
        val summary = buildString {
            append(timestampFormat.format(Date(time)))
            append(" ${state.importPath?.name ?: "?"}")
            append(" EXIF:${if (exif != null) "yes" else "no"}")
            distance(exif, device)?.let { append(" exif↔device=${it.toInt()}m") }
            distance(exif, manual)?.let { append(" exif↔pin=${it.toInt()}m") }
            distance(device, manual)?.let { append(" device↔pin=${it.toInt()}m") }
            device?.accuracyMeters?.let { append(" ±${it.toInt()}m") }
        }
        _uiState.update { it.copy(log = listOf(summary) + it.log) }
    }

    private fun recomputeChoice() {
        _uiState.update {
            if (it.photoUri == null) {
                it
            } else {
                it.copy(
                    choice = LocationChooser.chooseBest(
                        exif = it.exif?.fix,
                        device = it.device,
                        manual = it.manual,
                        capturedInApp = it.importPath == ImportPath.CAMERA,
                    ),
                )
            }
        }
    }

    private fun distance(a: LocationFix?, b: LocationFix?): Double? =
        if (a != null && b != null) LocationChooser.haversineMeters(a.point, b.point) else null

    private fun now() = System.currentTimeMillis()

    private fun Number?.fmt(): String = this?.toString().orEmpty()

    private companion object {
        const val CSV_HEADER = "timestamp,import_path,exif_opened_original," +
            "exif_lat,exif_lng,exif_error_m,device_lat,device_lng,device_accuracy_m," +
            "device_age_s,manual_lat,manual_lng,chosen_source," +
            "exif_device_m,exif_manual_m,device_manual_m"

        val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    }
}
