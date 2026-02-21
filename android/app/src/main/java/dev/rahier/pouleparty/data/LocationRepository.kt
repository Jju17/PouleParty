package dev.rahier.pouleparty.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {

    fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Get the last known location, or null if unavailable.
     */
    @Suppress("MissingPermission")
    suspend fun getLastLocation(): LatLng? {
        return try {
            val location = fusedLocationClient.lastLocation.await()
            if (location != null) LatLng(location.latitude, location.longitude) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Emit user location as Flow<LatLng>.
     * Uses distanceFilter ≈ 10m via smallestDisplacement to match iOS behaviour.
     */
    @Suppress("MissingPermission")
    fun locationFlow(): Flow<LatLng> = callbackFlow {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5_000L // 5 seconds interval
        )
            .setMinUpdateDistanceMeters(10f) // ≡ distanceFilter = 10 on iOS
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(LatLng(location.latitude, location.longitude))
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
    }
}
