package com.peru.expresdm.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

fun Context.hasAnyLocationPermission(): Boolean =
    hasFineLocationPermission() ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

fun Context.hasFineLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

fun Context.isLocationEnabled(): Boolean {
    val manager = getSystemService<LocationManager>() ?: return false
    return LocationManagerCompat.isLocationEnabled(manager)
}

/**
 * Posición actual del usuario para una acción puntual, como marcar una alerta.
 *
 * Si hay una lectura reciente se usa al momento; si no, se pide una nueva y se
 * espera como mucho [timeoutMs]. Devuelve `null` sin permiso o sin ubicación.
 */
suspend fun Context.currentLocation(timeoutMs: Long = 12_000L): Location? {
    if (!hasAnyLocationPermission()) return null
    val manager = getSystemService<LocationManager>() ?: return null

    val lastKnown = try {
        manager.getProviders(true)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    } catch (security: SecurityException) {
        null
    }
    if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < FRESH_LOCATION_MS) return lastKnown

    val provider = when {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> return lastKnown
    }

    val fresh = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine<Location?> { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(this@currentLocation),
                    Consumer { location -> if (continuation.isActive) continuation.resume(location) }
                )
            } catch (security: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
    return fresh ?: lastKnown
}

/** Una lectura de hace menos de esto sirve sin pedir otra. */
private const val FRESH_LOCATION_MS = 2 * 60_000L
