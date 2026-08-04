package com.sysadmindoc.billminder.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.BillRepository
import com.sysadmindoc.billminder.data.Payment
import com.sysadmindoc.billminder.wear.WearSync
import com.sysadmindoc.billminder.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val db = BillDatabase.getDatabase(context)
        val repo = BillRepository(db.billDao())

        when (intent.action) {
            "BILL_REMINDER" -> handleReminder(context, intent, repo)
            "MARK_PAID" -> handleMarkPaid(context, intent, repo)
            "SNOOZE" -> handleSnooze(context, intent)
            "SNOOZED_REMINDER" -> handleSnoozedReminder(context, intent)
            "REMINDER_DISMISSED" -> handleReminderDismissed(context, intent, repo)
            "CASCADE_REMINDER" -> handleCascadeReminder(context, intent, repo)
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                rescheduleAll(context, repo)
            }
        }
    }

    private fun handleReminder(context: Context, intent: Intent, repo: BillRepository) {
        val requestCode = intent.getLongExtra("request_code", -1)
        val daysBeforeDue = intent.getIntExtra("days_before_due", 1)
        val billId = if (requestCode >= 50000) requestCode - 50000 else requestCode

        CoroutineScope(Dispatchers.IO).launch {
            val bill = repo.getBillById(billId) ?: return@launch
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val payment = repo.getPaymentForBillDue(bill.id, nextDue)

            if (payment == null) {
                NotificationHelper.showReminderNotification(
                    context = context,
                    billId = bill.id,
                    billName = bill.name,
                    amount = bill.amount,
                    daysUntilDue = daysBeforeDue,
                    isAutoPay = bill.isAutoPay,
                    nextDueDate = nextDue,
                    currency = bill.currency
                )
            }

            ReminderScheduler.scheduleReminder(context, bill)
        }
    }

    private fun handleMarkPaid(context: Context, intent: Intent, repo: BillRepository) {
        val billId = intent.getLongExtra("bill_id", -1)
        val amount = intent.getDoubleExtra("amount", 0.0)
        if (billId == -1L) return

        CoroutineScope(Dispatchers.IO).launch {
            val bill = repo.getBillById(billId) ?: return@launch
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            if (repo.getPaymentForBillDue(billId, nextDue) == null) {
                repo.insertPayment(
                    Payment(
                        billId = billId,
                        amount = if (amount > 0.0) amount else bill.amount,
                        dueDate = nextDue,
                        currency = bill.currency
                    )
                )
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(billId.toInt())
            nm.cancel((billId + 20000).toInt())
            cancelCascade(context, billId)
            WearSync.sync(context)
            WidgetUpdater.updateAll(context)
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val billId = intent.getLongExtra("bill_id", -1)
        val billName = intent.getStringExtra("bill_name") ?: return
        val amount = intent.getDoubleExtra("amount", 0.0)
        val daysUntilDue = intent.getIntExtra("days_until_due", 0)
        val isAutoPay = intent.getBooleanExtra("is_auto_pay", false)
        val currency = intent.getStringExtra("currency") ?: "USD"
        val snoozeMinutes = intent.getIntExtra("snooze_minutes", 60)

        // Dismiss current notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(billId.toInt())

        // Schedule snooze alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "SNOOZED_REMINDER"
            putExtra("bill_id", billId)
            putExtra("bill_name", billName)
            putExtra("amount", amount)
            putExtra("currency", currency)
            putExtra("days_until_due", daysUntilDue)
            putExtra("is_auto_pay", isAutoPay)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, (billId + 60000).toInt(), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    private fun handleSnoozedReminder(context: Context, intent: Intent) {
        val billId = intent.getLongExtra("bill_id", -1)
        val billName = intent.getStringExtra("bill_name") ?: return
        val amount = intent.getDoubleExtra("amount", 0.0)
        val daysUntilDue = intent.getIntExtra("days_until_due", 0)
        val isAutoPay = intent.getBooleanExtra("is_auto_pay", false)
        val currency = intent.getStringExtra("currency") ?: "USD"

        NotificationHelper.showReminderNotification(
            context = context,
            billId = billId,
            billName = billName,
            amount = amount,
            daysUntilDue = daysUntilDue,
            isAutoPay = isAutoPay,
            currency = currency
        )
    }

    private fun handleReminderDismissed(context: Context, intent: Intent, repo: BillRepository) {
        val billId = intent.getLongExtra("bill_id", -1)
        if (billId == -1L) return

        CoroutineScope(Dispatchers.IO).launch {
            val bill = repo.getBillById(billId) ?: return@launch
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val payment = repo.getPaymentForBillDue(bill.id, nextDue)
            if (payment == null) {
                scheduleCascade(
                    context = context,
                    billId = bill.id,
                    billName = bill.name,
                    amount = bill.amount,
                    daysUntilDue = ((nextDue - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)).toInt(),
                    isAutoPay = bill.isAutoPay,
                    nextDueDate = nextDue,
                    currency = bill.currency
                )
            }
        }
    }

    private fun scheduleCascade(
        context: Context,
        billId: Long,
        billName: String,
        amount: Double,
        daysUntilDue: Int,
        isAutoPay: Boolean,
        nextDueDate: Long,
        currency: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        // Schedule 4-hour follow-up
        val cascade4hIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "CASCADE_REMINDER"
            putExtra("bill_id", billId)
            putExtra("bill_name", billName)
            putExtra("amount", amount)
            putExtra("currency", currency)
            putExtra("days_until_due", daysUntilDue)
            putExtra("is_auto_pay", isAutoPay)
            putExtra("next_due_date", nextDueDate)
            putExtra("cascade_level", 1)
        }
        val cascade4hPending = PendingIntent.getBroadcast(
            context, (billId + 70000).toInt(), cascade4hIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now + 4 * 60 * 60 * 1000L,
            cascade4hPending
        )

        // Schedule 24-hour follow-up
        val cascade24hIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "CASCADE_REMINDER"
            putExtra("bill_id", billId)
            putExtra("bill_name", billName)
            putExtra("amount", amount)
            putExtra("currency", currency)
            putExtra("days_until_due", daysUntilDue)
            putExtra("is_auto_pay", isAutoPay)
            putExtra("next_due_date", nextDueDate)
            putExtra("cascade_level", 2)
        }
        val cascade24hPending = PendingIntent.getBroadcast(
            context, (billId + 80000).toInt(), cascade24hIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            now + 24 * 60 * 60 * 1000L,
            cascade24hPending
        )
    }

    private fun handleCascadeReminder(context: Context, intent: Intent, repo: BillRepository) {
        val billId = intent.getLongExtra("bill_id", -1)
        val nextDueDate = intent.getLongExtra("next_due_date", 0)
        val cascadeLevel = intent.getIntExtra("cascade_level", 1)
        if (billId == -1L) return

        CoroutineScope(Dispatchers.IO).launch {
            val bill = repo.getBillById(billId) ?: return@launch
            val currentNextDue = ReminderScheduler.getNextDueDate(bill)
            if (currentNextDue != nextDueDate) return@launch

            // Check if already paid -- skip cascade if so
            val payment = repo.getPaymentForBillDue(billId, currentNextDue)
            if (payment != null) return@launch

            val now = System.currentTimeMillis()
            val daysUntilDue = ((nextDueDate - now) / (1000 * 60 * 60 * 24)).toInt()

            if (daysUntilDue < 0) {
                // Already overdue
                NotificationHelper.showOverdueNotification(
                    context = context,
                    billId = billId,
                    billName = bill.name,
                    amount = bill.amount,
                    daysPastDue = -daysUntilDue,
                    currency = bill.currency
                )
            } else {
                val escalationNote = when (cascadeLevel) {
                    1 -> " (Follow-up)"
                    else -> " (URGENT)"
                }
                NotificationHelper.showReminderNotification(
                    context = context,
                    billId = billId,
                    billName = "${bill.name}$escalationNote",
                    amount = bill.amount,
                    daysUntilDue = daysUntilDue,
                    isAutoPay = bill.isAutoPay,
                    nextDueDate = nextDueDate,
                    currency = bill.currency
                )
            }
        }
    }

    private fun cancelCascade(context: Context, billId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(
            (billId + 70000).toInt() to "CASCADE_REMINDER",
            (billId + 80000).toInt() to "CASCADE_REMINDER"
        ).forEach { (requestCode, action) ->
            val intent = Intent(context, ReminderReceiver::class.java).apply { this.action = action }
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let(alarmManager::cancel)
        }
    }

    private fun rescheduleAll(context: Context, repo: BillRepository) {
        CoroutineScope(Dispatchers.IO).launch {
            val bills = repo.getAllBillsList()
            ReminderScheduler.scheduleAllReminders(context, bills)
        }
    }
}
