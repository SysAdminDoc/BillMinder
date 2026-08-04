package com.sysadmindoc.billminder.notification

import android.content.Context

object GeofenceManager {
    fun register(
        context: Context,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        onResult: (Boolean, String?) -> Unit
    ) {
        onResult(false, "Home geofencing is available in the Play build")
    }

    fun unregister(context: Context, onResult: (Boolean) -> Unit = {}) {
        onResult(false)
    }
}
