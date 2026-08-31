package com.sysadmindoc.billminder.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * What the system will actually let a reminder do right now.
 *
 * Every one of these can be off without the app being told, so the settings screen reads them each
 * time rather than assuming the permission it declared was granted.
 */
data class ReminderPermissionState(
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
    val fullScreenAllowed: Boolean
) {
    /** True when reminders will still arrive, even if less precisely than intended. */
    val remindersWillArrive: Boolean get() = notificationsAllowed

    /** Plain-language summary for the settings row. */
    val summary: String
        get() = when {
            !notificationsAllowed -> "Notifications are off, so no reminder can reach you"
            !exactAlarmsAllowed -> "Reminders may arrive late because exact alarms are off"
            else -> "Reminders can arrive on time"
        }
}

object ReminderPermissions {

    fun read(context: Context): ReminderPermissionState {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            notificationManager.areNotificationsEnabled()
        }

        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        val fullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()

        return ReminderPermissionState(notifications, exact, fullScreen)
    }

    /** Opens the system screen that governs whichever permission is missing. */
    fun settingsIntent(context: Context, state: ReminderPermissionState): Intent = when {
        !state.notificationsAllowed ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        !state.exactAlarmsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null))

        else ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
