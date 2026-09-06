package com.rakdatak.app.workout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

data class PhoneWorkoutMetrics(
    val distanceMeters: Double,
    val gpsAvailable: Boolean,
    val locationPermissionGranted: Boolean,
)

/**
 * Lightweight phone-side GPS distance tracker. Heart-rate is intentionally not synthesized on the
 * phone; live HR will come from the paired Wear OS workout when that sync layer is active.
 */
@Composable
fun rememberPhoneWorkoutMetrics(
    active: Boolean,
    initialDistanceMeters: Double,
    onDistanceChanged: (Double) -> Unit,
): PhoneWorkoutMetrics {
    val context = LocalContext.current
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    var permissionGranted by remember {
        mutableStateOf(hasLocationPermission(context))
    }
    var distanceMeters by remember(initialDistanceMeters) {
        mutableStateOf(initialDistanceMeters.coerceAtLeast(0.0))
    }
    var gpsAvailable by remember {
        mutableStateOf(runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted || hasLocationPermission(context)
    }

    LaunchedEffect(active) {
        if (active && !permissionGranted) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    DisposableEffect(active, permissionGranted, locationManager) {
        if (!active || !permissionGranted) {
            return@DisposableEffect onDispose { }
        }

        var lastAcceptedLocation: Location? = null

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (location.accuracy > MAX_ACCEPTED_ACCURACY_METERS) return

                val previous = lastAcceptedLocation
                lastAcceptedLocation = location
                if (previous == null) return

                val segmentMeters = previous.distanceTo(location)
                val plausibleSegment = segmentMeters in MIN_SEGMENT_METERS..MAX_SEGMENT_METERS
                val plausibleSpeed = !location.hasSpeed() || location.speed <= MAX_PLAUSIBLE_SPEED_MPS

                if (plausibleSegment && plausibleSpeed) {
                    distanceMeters += segmentMeters.toDouble()
                    onDistanceChanged(distanceMeters)
                }
            }

            override fun onProviderEnabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) gpsAvailable = true
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) gpsAvailable = false
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        val registered = runCatching {
            gpsAvailable = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_UPDATE_INTERVAL_MS,
                LOCATION_MIN_DISTANCE_METERS,
                listener,
            )
            true
        }.getOrDefault(false)

        if (!registered) gpsAvailable = false

        onDispose {
            runCatching { locationManager.removeUpdates(listener) }
        }
    }

    return PhoneWorkoutMetrics(
        distanceMeters = distanceMeters,
        gpsAvailable = gpsAvailable,
        locationPermissionGranted = permissionGranted,
    )
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

private const val LOCATION_UPDATE_INTERVAL_MS = 1_000L
private const val LOCATION_MIN_DISTANCE_METERS = 1f
private const val MAX_ACCEPTED_ACCURACY_METERS = 40f
private const val MIN_SEGMENT_METERS = 1.5f
private const val MAX_SEGMENT_METERS = 120f
private const val MAX_PLAUSIBLE_SPEED_MPS = 12.5f
