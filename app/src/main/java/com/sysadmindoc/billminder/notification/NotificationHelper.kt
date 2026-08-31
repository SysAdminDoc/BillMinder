package com.sysadmindoc.billminder.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sysadmindoc.billminder.MainActivity
import com.sysadmindoc.billminder.R
import com.sysadmindoc.billminder.data.CurrencyFormatter
import com.sysadmindoc.billminder.security.PrivacyText
import com.sysadmindoc.billminder.security.SecurityPrefs

object NotificationHelper {

    const val CHANNEL_REMINDERS = "bill_reminders"
    const val CHANNEL_OVERDUE = "bill_overdue"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            "Bill Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Upcoming bill due date reminders"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
        }

        val overdueChannel = NotificationChannel(
            CHANNEL_OVERDUE,
            "Overdue Bills",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for overdue unpaid bills"
            enableVibration(true)
        }

        nm.createNotificationChannel(reminderChannel)
        nm.createNotificationChannel(overdueChannel)
    }

    /** Clears every notification this app can show for one bill. */
    fun cancelAll(context: Context, billId: Long) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        AlarmIds.allNotificationIds(billId).forEach(nm::cancel)
    }

    fun cancelDisplayed(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()
    }

    fun showReminderNotification(
        context: Context,
        billId: Long,
        billName: String,
        amount: Double,
        daysUntilDue: Int,
        isAutoPay: Boolean,
        nextDueDate: Long = 0L,
        cycleKey: String = "",
        currency: String = "USD"
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("bill_id", billId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, AlarmIds.code(billId, AlarmSlot.OPEN_BILL), intent.apply { data = AlarmIds.uri(billId, AlarmSlot.OPEN_BILL) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
            putExtra("bill_id", billId)
            putExtra("bill_name", billName)
            putExtra("amount", amount)
            putExtra("currency", currency)
            putExtra("days_until_due", daysUntilDue)
            putExtra("is_auto_pay", isAutoPay)
            putExtra("next_due_date", nextDueDate)
            putExtra(ReminderScheduler.EXTRA_CYCLE_KEY, cycleKey)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, AlarmIds.code(billId, AlarmSlot.FULL_SCREEN), fullScreenIntent.apply { data = AlarmIds.uri(billId, AlarmSlot.FULL_SCREEN) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Mark paid action
        val markPaidIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "MARK_PAID"
            putExtra(ReminderScheduler.EXTRA_CYCLE_KEY, cycleKey)
            putExtra("bill_id", billId)
            putExtra("amount", amount)
            putExtra("currency", currency)
        }
        val markPaidPending = PendingIntent.getBroadcast(
            context, AlarmIds.code(billId, AlarmSlot.MARK_PAID), markPaidIntent.apply { data = AlarmIds.uri(billId, AlarmSlot.MARK_PAID) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze 1 hour action
        val snooze1hIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "SNOOZE"
            putExtra(ReminderScheduler.EXTRA_CYCLE_KEY, cycleKey)
            putExtra("bill_id", billId)
            putExtra("bill_name", billName)
            putExtra("amount", amount)
            putExtra("currency", currency)
            putExtra("days_until_due", daysUntilDue)
            putExtra("is_auto_pay", isAutoPay)
            putExtra("snooze_minutes", 60)
        }
        val snooze1hPending = PendingIntent.getBroadcast(
            context, AlarmIds.code(billId, AlarmSlot.SNOOZE_ONE_HOUR), snooze1hIntent.apply { data = AlarmIds.uri(billId, AlarmSlot.SNOOZE_ONE_HOUR) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze tomorrow action
        val snoozeTmrwIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "SNOOZE"
            putExtra(ReminderScheduler.EXTRA_CYCLE_KEY, cycleKey)
            putExtra("bill_id", billId)
            putExtra("bill_name", billName)
            putExtra("amount", amount)
            putExtra("currency", currency)
            putExtra("days_until_due", daysUntilDue)
            putExtra("is_auto_pay", isAutoPay)
            putExtra("snooze_minutes", 60 * 24)
        }
        val snoozeTmrwPending = PendingIntent.getBroadcast(
            context, AlarmIds.code(billId, AlarmSlot.SNOOZE_TOMORROW), snoozeTmrwIntent.apply { data = AlarmIds.uri(billId, AlarmSlot.SNOOZE_TOMORROW) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissedIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "REMINDER_DISMISSED"
            putExtra("bill_id", billId)
            putExtra("next_due_date", nextDueDate)
            putExtra(ReminderScheduler.EXTRA_CYCLE_KEY, cycleKey)
        }
        val dismissedPending = PendingIntent.getBroadcast(
            context, AlarmIds.code(billId, AlarmSlot.REMINDER_DISMISSED), dismissedIntent.apply { data = AlarmIds.uri(billId, AlarmSlot.REMINDER_DISMISSED) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dueText = when {
            daysUntilDue == 0 -> "due today"
            daysUntilDue == 1 -> "due tomorrow"
            daysUntilDue > 1 -> "due in $daysUntilDue days"
            else -> "overdue by ${-daysUntilDue} day(s)"
        }
        val autoPayNote = if (isAutoPay) " (Auto-Pay)" else ""
        val privacyEnabled = SecurityPrefs.maskExternalContent(context)
        val visibleBillName = PrivacyText.externalBillName(billName, privacyEnabled)
        val visibleAmount = PrivacyText.externalAmount(
            CurrencyFormatter.format(amount, currency),
            privacyEnabled
        )

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$visibleBillName · $visibleAmount$autoPayNote")
            .setContentText("Bill is $dueText")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$visibleBillName is $dueText.\nAmount: $visibleAmount$autoPayNote"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(
                if (ReminderPrefs.isFullScreenEnabled(context)) {
                    NotificationCompat.CATEGORY_ALARM
                } else {
                    NotificationCompat.CATEGORY_REMINDER
                }
            )
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification, "Paid", markPaidPending)
            .addAction(R.drawable.ic_notification, "1hr", snooze1hPending)
            .addAction(R.drawable.ic_notification, "Tomorrow", snoozeTmrwPending)
            .setDeleteIntent(dismissedPending)
            .setAutoCancel(true)
        // The preference alone is not enough: Android 14 denies full-screen intents to apps in
        // this category, and attaching one anyway silently downgrades the alert.
        if (ReminderPrefs.isFullScreenEnabled(context) && ReminderPermissions.canUseFullScreen(context)) {
            notificationBuilder.setFullScreenIntent(fullScreenPending, true)
        }
        val notification = notificationBuilder.build()

        nm.notify(AlarmIds.notificationId(billId, AlarmSlot.REMINDER_NOTIFICATION), notification)
    }

    fun showOverdueNotification(
        context: Context,
        billId: Long,
        billName: String,
        amount: Double,
        daysPastDue: Int,
        cycleKey: String = "",
        currency: String = "USD"
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("bill_id", billId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, AlarmIds.code(billId, AlarmSlot.OVERDUE_OPEN_BILL), intent.apply { data = AlarmIds.uri(billId, AlarmSlot.OVERDUE_OPEN_BILL) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fullScreenIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
            putExtra("bill_id", billId)
            putExtra("bill_name", billName)
            putExtra("amount", amount)
            putExtra("currency", currency)
            putExtra("days_until_due", -daysPastDue)
            putExtra("next_due_date", 0L)
            putExtra(ReminderScheduler.EXTRA_CYCLE_KEY, cycleKey)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, AlarmIds.code(billId, AlarmSlot.OVERDUE_FULL_SCREEN), fullScreenIntent.apply { data = AlarmIds.uri(billId, AlarmSlot.OVERDUE_FULL_SCREEN) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markPaidIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "MARK_PAID"
            putExtra(ReminderScheduler.EXTRA_CYCLE_KEY, cycleKey)
            putExtra("bill_id", billId)
            putExtra("amount", amount)
        }
        val markPaidPending = PendingIntent.getBroadcast(
            context, AlarmIds.code(billId, AlarmSlot.MARK_PAID), markPaidIntent.apply { data = AlarmIds.uri(billId, AlarmSlot.MARK_PAID) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val privacyEnabled = SecurityPrefs.maskExternalContent(context)
        val visibleBillName = PrivacyText.externalBillName(billName, privacyEnabled)
        val visibleAmount = PrivacyText.externalAmount(
            CurrencyFormatter.format(amount, currency),
            privacyEnabled
        )

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_OVERDUE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("OVERDUE: $visibleBillName")
            .setContentText("$visibleAmount is $daysPastDue day(s) past due!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification, "Paid", markPaidPending)
            .setOngoing(true)
        // The preference alone is not enough: Android 14 denies full-screen intents to apps in
        // this category, and attaching one anyway silently downgrades the alert.
        if (ReminderPrefs.isFullScreenEnabled(context) && ReminderPermissions.canUseFullScreen(context)) {
            notificationBuilder.setFullScreenIntent(fullScreenPending, true)
        }
        val notification = notificationBuilder.build()

        nm.notify(AlarmIds.notificationId(billId, AlarmSlot.OVERDUE_NOTIFICATION), notification)
    }
}
