package com.sysadmindoc.billminder.notification

import android.content.Context

data class HomeGeofenceConfig(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val enabled: Boolean
)

object GeofencePrefs {
    private const val PREFS_NAME = "billminder_geofence"
    private const val LATITUDE = "latitude"
    private const val LONGITUDE = "longitude"
    private const val RADIUS = "radius_meters"
    private const val ENABLED = "enabled"

    fun get(context: Context): HomeGeofenceConfig? {
        val prefs = prefs(context)
        if (!prefs.contains(LATITUDE) || !prefs.contains(LONGITUDE)) return null
        return HomeGeofenceConfig(
            latitude = prefs.getString(LATITUDE, null)?.toDoubleOrNull() ?: return null,
            longitude = prefs.getString(LONGITUDE, null)?.toDoubleOrNull() ?: return null,
            radiusMeters = prefs.getFloat(RADIUS, 150f),
            enabled = prefs.getBoolean(ENABLED, false)
        )
    }

    fun save(context: Context, latitude: Double, longitude: Double, radiusMeters: Float) {
        prefs(context).edit()
            .putString(LATITUDE, latitude.toString())
            .putString(LONGITUDE, longitude.toString())
            .putFloat(RADIUS, radiusMeters)
            .putBoolean(ENABLED, true)
            .apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
