package com.example.myweather.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.myweather.LocationData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await   // 关键导入

interface LocationClient {
    fun getLocation(): Flow<Resource<LocationData>>
}

class DefaultLocationClient(
    private val context: Context,
    private val client: FusedLocationProviderClient
) : LocationClient {
    override fun getLocation(): Flow<Resource<LocationData>> = callbackFlow {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            trySend(Resource.Error("Location permission not granted"))
            close()
            return@callbackFlow
        }

        // 最后已知位置（快速）
        try {
            val lastLocation: Location? = client.lastLocation.await()
            if (lastLocation != null) {
                trySend(Resource.Success(LocationData(lastLocation.latitude, lastLocation.longitude)))
                close()
                return@callbackFlow
            }
        } catch (_: Exception) { }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        var isLocationReceived = false
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.lastOrNull()?.let { location ->
                    if (!isLocationReceived) {
                        isLocationReceived = true
                        trySend(Resource.Success(LocationData(location.latitude, location.longitude)))
                        close()
                    }
                }
            }
        }

        trySend(Resource.Loading())

        val timeoutJob = launch {
            delay(12000L)
            if (!isLocationReceived) {
                val defaultLocation = LocationData(39.91489, 116.40387)
                trySend(Resource.Success(defaultLocation))
                close()
            }
        }

        client.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            .addOnFailureListener { error ->
                timeoutJob.cancel()
                if (!isLocationReceived) {
                    trySend(Resource.Error("Location request failed: ${error.localizedMessage}"))
                    close()
                }
            }

        awaitClose {
            timeoutJob.cancel()
            client.removeLocationUpdates(locationCallback)
        }
    }
}

sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T> : Resource<T>()
}