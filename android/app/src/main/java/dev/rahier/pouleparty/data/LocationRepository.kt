package dev.rahier.pouleparty.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.mapbox.geojson.Point
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.rahier.pouleparty.AppConstants
import dev.rahier.pouleparty.service.LocationForegroundService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {

    fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocationPermission(): Boolean {
        // Pre-Android 10 (API 29) `ACCESS_BACKGROUND_LOCATION` didn't exist
        // as a separate runtime permission, granting fine location included
        // background implicitly. `checkSelfPermission` reports DENIED on
        // those devices even though the app can track in background, so we
        // treat fine as sufficient there.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return hasFineLocationPermission()
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get the last known location, or null if unavailable.
     *
     * Returns null instead of throwing when permission is missing so callers
     * can degrade gracefully (the @Suppress is now scoped to the actual call,
     * with a runtime guard above it).
     */
    suspend fun getLastLocation(): Point? {
        if (!hasFineLocationPermission()) return null
        return try {
            @Suppress("MissingPermission")
            val location = fusedLocationClient.lastLocation.await()
            if (location != null) Point.fromLngLat(location.longitude, location.latitude) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Start the location foreground service.
     *
     * Must be called while the app is in the foreground (typically from a
     * ViewModel's `init` block, synchronously). Android 12+ throws
     * `ForegroundServiceStartNotAllowedException` when `startForegroundService`
     * is invoked from the background — the `FOREGROUND_SERVICE_LOCATION`
     * exemption only kicks in when `ACCESS_BACKGROUND_LOCATION` is granted at
     * runtime, and many users grant only "while using the app". Keeping the
     * start outside of [locationFlow] guarantees it doesn't fire after a
     * suspended `delay()` (e.g. while waiting for the hunter head-start),
     * which is exactly when the app may already be backgrounded.
     *
     * Wrapped in try/catch as a belt-and-braces — if the FGS can't start we
     * log and carry on; foreground tracking still works, only the
     * backgrounded-during-game case silently degrades instead of crashing.
     */
    fun startTrackingService() {
        try {
            LocationForegroundService.start(context)
        } catch (e: Exception) {
            android.util.Log.w(
                "LocationRepository",
                "Failed to start location foreground service",
                e
            )
        }
    }

    /** Stop the location foreground service. Safe to call multiple times. */
    fun stopTrackingService() {
        LocationForegroundService.stop(context)
    }

    /**
     * Emit user location as Flow<Point>.
     * Uses distanceFilter ≈ 10m via smallestDisplacement to match iOS behaviour.
     *
     * The foreground service is started/stopped separately via
     * [startTrackingService] / [stopTrackingService] — not here — so the
     * service goes up while the app is guaranteed to be in the foreground
     * (ViewModel init), rather than after a suspended `delay()` that could
     * straddle a backgrounding event.
     */
    @Suppress("MissingPermission")
    fun locationFlow(): Flow<Point> = callbackFlow {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            AppConstants.LOCATION_UPDATE_INTERVAL_MS
        )
            .setMinUpdateDistanceMeters(AppConstants.LOCATION_MIN_DISTANCE_METERS)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(Point.fromLngLat(location.longitude, location.latitude))
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }.distinctUntilChanged()
}
