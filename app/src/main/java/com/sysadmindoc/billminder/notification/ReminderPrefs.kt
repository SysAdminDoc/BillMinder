package com.sysadmindoc.billminder.notification

import android.content.Context

object ReminderPrefs {
    private const val PREFS_NAME = "billminder_reminders"
    private const val FULL_SCREEN_ENABLED = "full_screen_enabled"
    private const val VACATION_MODE = "vacation_mode"

    fun isFullScreenEnabled(context: Context): Boolean =
        prefs(context).getBoolean(FULL_SCREEN_ENABLED, false)

    fun setFullScreenEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(FULL_SCREEN_ENABLED, enabled).apply()
    }

    fun isVacationMode(context: Context): Boolean =
        prefs(context).getBoolean(VACATION_MODE, false)

    fun setVacationMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(VACATION_MODE, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
