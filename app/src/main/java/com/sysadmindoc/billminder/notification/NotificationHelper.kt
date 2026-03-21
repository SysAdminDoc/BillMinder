package com.sysadmindoc.billminder.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sysadmindoc.billminder.MainActivity
import com.sysadmindoc.billminder.R

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

    fun showReminderNotification(
        context: Context,
        billId: Long,
        billName: String,
        amount: Double,
        daysUntilDue: Int,
        isAutoPay: Boolean
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("bill_id", billId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, billId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Mark paid action
        val markPaidIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "MARK_PAID"
            putExtra("bill_id", billId)
            putExtra("amount", amount)
        }
        val markPaidPending = PendingIntent.getBroadcast(
            context, (billId + 10000).toInt(), markPaidIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze 1 hour action
        val snooze1hIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "SNOOZE"
            putExtra("bill_id", billId)
            putExtra("bill_name", billName)
            putExtra("amount", amount)
            putExtra("days_until_due", daysUntilDue)
            putExtra("is_auto_pay", isAutoPay)
            putExtra("snooze_minutes", 60)
        }
        val snooze1hPending = PendingIntent.getBroadcast(
            context, (billId + 30000).toInt(), snooze1hIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze tomorrow action
        val snoozeTmrwIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "SNOOZE"
            putExtra("bill_id", billId)
            putExtra("bill_name", billName)
            putExtra("amount", amount)
            putExtra("days_until_due", daysUntilDue)
            putExtra("is_auto_pay", isAutoPay)
            putExtra("snooze_minutes", 60 * 24)
        }
        val snoozeTmrwPending = PendingIntent.getBroadcast(
            context, (billId + 40000).toInt(), snoozeTmrwIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dueText = when {
            daysUntilDue == 0 -> "due today"
            daysUntilDue == 1 -> "due tomorrow"
            daysUntilDue > 1 -> "due in $daysUntilDue days"
            else -> "overdue by ${-daysUntilDue} day(s)"
        }
        val autoPayNote = if (isAutoPay) " (Auto-Pay)" else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$billName - $${"%.2f".format(amount)}$autoPayNote")
            .setContentText("Bill is $dueText")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$billName is $dueText.\nAmount: $${"%.2f".format(amount)}$autoPayNote"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification, "Paid", markPaidPending)
            .addAction(R.drawable.ic_notification, "1hr", snooze1hPending)
            .addAction(R.drawable.ic_notification, "Tomorrow", snoozeTmrwPending)
            .setAutoCancel(true)
            .build()

        nm.notify(billId.toInt(), notification)
    }

    fun showOverdueNotification(
        context: Context,
        billId: Long,
        billName: String,
        amount: Double,
        daysPastDue: Int
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("bill_id", billId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, (billId + 20000).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markPaidIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "MARK_PAID"
            putExtra("bill_id", billId)
            putExtra("amount", amount)
        }
        val markPaidPending = PendingIntent.getBroadcast(
            context, (billId + 10000).toInt(), markPaidIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_OVERDUE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("OVERDUE: $billName")
            .setContentText("$${"%.2f".format(amount)} is $daysPastDue day(s) past due!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification, "Paid", markPaidPending)
            .setOngoing(true)
            .build()

        nm.notify((billId + 20000).toInt(), notification)
    }
}
