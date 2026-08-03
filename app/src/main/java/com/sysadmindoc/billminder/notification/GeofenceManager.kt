package com.sysadmindoc.billminder.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

object GeofenceManager {
    private const val REQUEST_CODE = 91001
    private const val REQUEST_ID = "billminder_home"

    fun register(
        context: Context,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (!hasLocationPermission(context)) {
            onResult(false, "Location permission is required")
            return
        }
        val geofence = Geofence.Builder()
            .setRequestId(REQUEST_ID)
            .setCircularRegion(latitude, longitude, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL)
            .setLoiteringDelay(5 * 60 * 1000)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        try {
            LocationServices.getGeofencingClient(context)
                .addGeofences(request, pendingIntent(context))
                .addOnSuccessListener { onResult(true, null) }
                .addOnFailureListener { error -> onResult(false, error.message ?: "Unable to register home geofence") }
        } catch (error: SecurityException) {
            onResult(false, error.message ?: "Location permission is required")
        } catch (error: IllegalArgumentException) {
            onResult(false, error.message ?: "Invalid home geofence")
        }
    }

    fun unregister(context: Context, onResult: (Boolean) -> Unit = {}) {
        LocationServices.getGeofencingClient(context)
            .removeGeofences(pendingIntent(context))
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            android.content.Intent(context, HomeGeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)
}
