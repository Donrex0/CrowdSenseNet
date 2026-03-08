package com.example.crowdsensenet.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.Manifest
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume

object LocationUtils {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    fun initialize(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        if (!::fusedLocationClient.isInitialized) {
            initialize(context)
        }

        return suspendCancellableCoroutine { cont ->
            // First try to get last known location
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(location)
                    } else {
                        // If last location is null, request fresh location
                        requestFreshLocation(cont)
                    }
                }
                .addOnFailureListener { exception ->
                    // If last location fails, request fresh location
                    requestFreshLocation(cont)
                }
            
            cont.invokeOnCancellation {
                // Handle cancellation if needed
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(cont: kotlinx.coroutines.CancellableContinuation<Location?>) {
        val locationRequest = LocationRequest.Builder(10000) // 10 seconds timeout
            .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdates(1) // Only need one update
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    cont.resume(location)
                } ?: run {
                    cont.resume(null)
                }
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )

        // Timeout after 10 seconds using a simpler approach
        cont.invokeOnCancellation {
            fusedLocationClient.removeLocationUpdates(callback)
        }
        
        // Use a simple timeout without complex coroutine context
        kotlinx.coroutines.GlobalScope.launch {
            delay(10000)
            if (cont.context[kotlin.coroutines.CancellationException::class]?.let { false } ?: true) {
                // Not cancelled, proceed with timeout
                try {
                    fusedLocationClient.removeLocationUpdates(callback)
                    cont.resume(null)
                } catch (e: Exception) {
                    // Already resumed, ignore
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(context: Context, intervalMs: Long = 5000L): Flow<Location> = callbackFlow {
        if (!::fusedLocationClient.isInitialized) {
            initialize(context)
        }

        val locationRequest = LocationRequest.Builder(intervalMs)
            .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    trySend(location)
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

    fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun formatCoordinates(latitude: Double, longitude: Double): String {
        return "Latitude: %.6f\nLongitude: %.6f".format(latitude, longitude)
    }
}
