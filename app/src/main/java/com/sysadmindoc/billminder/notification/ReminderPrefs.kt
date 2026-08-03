package com.sysadmindoc.billminder.notification

import android.content.Context

object ReminderPrefs {
    private const val PREFS_NAME = "billminder_reminders"
    private const val FULL_SCREEN_ENABLED = "full_screen_enabled"

    fun isFullScreenEnabled(context: Context): Boolean =
        prefs(context).getBoolean(FULL_SCREEN_ENABLED, false)

    fun setFullScreenEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(FULL_SCREEN_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
