package com.example.taxiradar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

object ScreenReaderLocation {
    fun permitted(c: Context) = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun fresh(location: Location?) = location?.takeIf {
        SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0..120_000_000_000L && it.hasAccuracy() && it.accuracy <= 200f
    }
    @SuppressLint("MissingPermission")
    suspend fun fix(c: Context): Location? {
        if (!permitted(c)) return null
        // A running radar normally has a recent GPS fix already; do not wait for a new fix on every offer.
        val locationManager = c.getSystemService(LocationManager::class.java)
        val recent = runCatching { locationManager?.let { manager -> manager.getProviders(true).mapNotNull {
            fresh(manager.getLastKnownLocation(it))?.takeIf { fix ->
                SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos <= 15_000_000_000L
            }
        }.maxByOrNull { it.elapsedRealtimeNanos } } }.getOrNull()
        if (recent != null) return recent
        val fused = withTimeoutOrNull(5000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancel = CancellationTokenSource()
                cont.invokeOnCancellation { cancel.cancel() }
                try {
                    LocationServices.getFusedLocationProviderClient(c).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
                        .addOnSuccessListener { if (cont.isActive) cont.resumeWith(Result.success(fresh(it))) }
                        .addOnFailureListener { if (cont.isActive) cont.resumeWith(Result.success(null)) }
                } catch (_: Exception) { if (cont.isActive) cont.resumeWith(Result.success(null)) }
            }
        }
        if (fused != null) return fused
        val manager = c.getSystemService(LocationManager::class.java) ?: return null
        val cached = runCatching { manager.getProviders(true).mapNotNull { fresh(manager.getLastKnownLocation(it)) }.maxByOrNull { it.elapsedRealtimeNanos } }.getOrNull()
        if (cached != null) return cached
        val provider = runCatching { listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { manager.isProviderEnabled(it) } }.getOrNull() ?: return null
        return withTimeoutOrNull(5000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val fix = fresh(location) ?: return
                        manager.removeUpdates(this)
                        if (cont.isActive) cont.resumeWith(Result.success(fix))
                    }
                    @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
                try { manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
                catch (_: Exception) { if (cont.isActive) cont.resumeWith(Result.success(null)) }
            }
        }
    }
}
