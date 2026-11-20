package edu.csuci.tweeter.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.TimeUnit.HOURS
import kotlinx.coroutines.tasks.await

/** How long [DeviceLocationProvider.getAndSaveCurrentFix] waits for a fix by default. */
private const val DEFAULT_TIMEOUT_MILLIS = 20_000L

// How old a location the system already has may be for saveRoughLocation to use it instead of
// asking for a fix: rare animals are looked for within kilometers of the user, who seldom goes
// far in an hour.
private val ROUGH_LOCATION_MAX_AGE_MILLIS = HOURS.toMillis(1)

// How long saveRoughLocation waits for a fix when the system has no recent location. A fix from
// Wi-Fi and cell towers comes within seconds when it comes at all.
private const val ROUGH_LOCATION_TIMEOUT_MILLIS = 10_000L

/**
 * Gets location fixes from the fused location provider, and saves each in [lastLocationStore] as
 * where the device last was, where notifications of posts near the user look for where they are.
 *
 * @param context Any context; only its application context is kept.
 */
class DeviceLocationProvider(context: Context, private val lastLocationStore: LastLocationStore) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    /** Returns whether the app may request a location fix (fine or coarse). */
    fun hasPermission(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Requests a fresh high-accuracy fix, which can take as long as [timeoutMillis] and uses GPS,
     * saves it as where the device last was, and returns it. Returns null, saving nothing, if
     * permission is missing, location is off, or no fix arrives in time.
     */
    suspend fun getAndSaveCurrentFix(timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): LocationFix? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .setDurationMillis(timeoutMillis)
            .build()
        return getAndSaveFix(request)
    }

    /**
     * Saves roughly where the device is as where it last was, cheaply: a location the system got
     * within the last hour, for this app or another, or else a fix from Wi-Fi and cell towers
     * rather than GPS. Saves nothing if permission is missing, location is off, or no fix comes.
     */
    suspend fun saveRoughLocation() {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(ROUGH_LOCATION_MAX_AGE_MILLIS)
            .setDurationMillis(ROUGH_LOCATION_TIMEOUT_MILLIS)
            .build()
        getAndSaveFix(request)
    }

    private suspend fun getAndSaveFix(request: CurrentLocationRequest): LocationFix? {
        val fix = fixFor(request) ?: return null
        lastLocationStore.save(fix.point)
        return fix
    }

    private suspend fun fixFor(request: CurrentLocationRequest): LocationFix? {
        if (!hasPermission()) {
            return null
        }
        val cancellation = CancellationTokenSource()
        return try {
            @Suppress("MissingPermission") // Checked by hasPermission() above.
            val location = client.getCurrentLocation(request, cancellation.token).await()
            location?.let {
                LocationFix(
                    point = GeoPoint(it.latitude, it.longitude),
                    source = LocationSource.DEVICE,
                    accuracyMeters = if (it.hasAccuracy()) it.accuracy else null,
                    timeMillis = it.time
                )
            }
        } catch (e: SecurityException) {
            null
        } finally {
            cancellation.cancel()
        }
    }
}
