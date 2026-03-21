package com.sysadmindoc.billminder.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.BillRepository
import com.sysadmindoc.billminder.data.Payment
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
                    isAutoPay = bill.isAutoPay
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
            repo.insertPayment(
                Payment(billId = billId, amount = amount, dueDate = nextDue)
            )
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(billId.toInt())
            nm.cancel((billId + 20000).toInt())
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val billId = intent.getLongExtra("bill_id", -1)
        val billName = intent.getStringExtra("bill_name") ?: return
        val amount = intent.getDoubleExtra("amount", 0.0)
        val daysUntilDue = intent.getIntExtra("days_until_due", 0)
        val isAutoPay = intent.getBooleanExtra("is_auto_pay", false)
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

        NotificationHelper.showReminderNotification(
            context = context,
            billId = billId,
            billName = billName,
            amount = amount,
            daysUntilDue = daysUntilDue,
            isAutoPay = isAutoPay
        )
    }

    private fun rescheduleAll(context: Context, repo: BillRepository) {
        CoroutineScope(Dispatchers.IO).launch {
            val bills = repo.getAllBillsList()
            ReminderScheduler.scheduleAllReminders(context, bills)
        }
    }
}
