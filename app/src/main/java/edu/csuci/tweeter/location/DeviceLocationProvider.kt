package edu.csuci.tweeter.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

/** Gets live location fixes from the fused location provider. */
class DeviceLocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    /** True if the app may request a location fix (fine or coarse). */
    fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any {
                ContextCompat.checkSelfPermission(appContext, it) ==
                    PackageManager.PERMISSION_GRANTED
            }

    /**
     * Requests a fresh high-accuracy fix, or returns null if permission is missing, location is
     * off, or no fix arrives within [timeoutMillis].
     */
    suspend fun currentFix(timeoutMillis: Long = 20_000): LocationFix? {
        if (!hasPermission()) {
            return null
        }
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .setDurationMillis(timeoutMillis)
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            @Suppress("MissingPermission") // Checked by hasPermission() above.
            val location = client.getCurrentLocation(request, cancellation.token).await()
            location?.let {
                LocationFix(
                    point = GeoPoint(it.latitude, it.longitude),
                    source = LocationSource.DEVICE,
                    accuracyMeters = if (it.hasAccuracy()) it.accuracy else null,
                    timeMillis = it.time,
                )
            }
        } catch (e: SecurityException) {
            null
        } finally {
            cancellation.cancel()
        }
    }
}
