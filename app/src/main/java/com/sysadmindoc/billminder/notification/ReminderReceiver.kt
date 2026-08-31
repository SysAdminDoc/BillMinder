package com.sysadmindoc.billminder.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.BillRepository
import com.sysadmindoc.billminder.data.Payment
import com.sysadmindoc.billminder.domain.CycleEngine
import com.sysadmindoc.billminder.wear.WearSync
import com.sysadmindoc.billminder.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

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
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON" -> rescheduleAll(context, repo)
        }
    }

    /**
     * Runs [block] with the broadcast kept alive. A plain coroutine launched from `onReceive` can
     * outlive the receiver and be killed mid-write, so every database path goes through here.
     */
    private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleReminder(context: Context, intent: Intent, repo: BillRepository) {
        val requestCode = intent.getLongExtra("request_code", -1)
        val daysBeforeDue = intent.getIntExtra("days_before_due", 1)
        val cycleKey = intent.getStringExtra("cycle_key").orEmpty()
        val billId = if (requestCode >= ReminderScheduler.SECOND_REMINDER_OFFSET) {
            requestCode - ReminderScheduler.SECOND_REMINDER_OFFSET
        } else {
            requestCode
        }

        runAsync {
            val bill = repo.getBillById(billId) ?: return@runAsync
            val cycleDate = CycleEngine.parseCycleKey(cycleKey)
                ?: repo.currentCycleFor(bill)
                ?: return@runAsync
            val key = CycleEngine.cycleKey(cycleDate)
            val dueAt = CycleEngine.dueInstant(cycleDate)

            if (repo.getPaymentForCycle(bill.id, key) == null) {
                NotificationHelper.showReminderNotification(
                    context = context,
                    billId = bill.id,
                    billName = bill.name,
                    amount = bill.amount,
                    daysUntilDue = daysBeforeDue,
                    isAutoPay = bill.isAutoPay,
                    nextDueDate = dueAt,
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

        runAsync {
            val bill = repo.getBillById(billId) ?: return@runAsync
            if (ReminderPrefs.isVacationMode(context) && bill.isAutoPay) {
                ReminderScheduler.scheduleReminder(context, bill)
                return@runAsync
            }
            val cycle = repo.currentCycleFor(bill) ?: return@runAsync
            repo.insertPayment(
                Payment(
                    billId = billId,
                    amount = if (amount > 0.0) amount else bill.amount,
                    dueDate = CycleEngine.dueInstant(cycle),
                    currency = bill.currency,
                    cycleKey = CycleEngine.cycleKey(cycle)
                )
            )
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
            context, (billId + ReminderScheduler.SNOOZE_OFFSET).toInt(), snoozeIntent,
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

        runAsync {
            val bill = repo.getBillById(billId) ?: return@runAsync
            val cycle = repo.currentCycleFor(bill) ?: return@runAsync
            val key = CycleEngine.cycleKey(cycle)
            if (repo.getPaymentForCycle(bill.id, key) == null) {
                scheduleCascade(
                    context = context,
                    billId = bill.id,
                    billName = bill.name,
                    amount = bill.amount,
                    daysUntilDue = java.time.temporal.ChronoUnit.DAYS
                        .between(LocalDate.now(), cycle).toInt(),
                    isAutoPay = bill.isAutoPay,
                    cycleKey = key,
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
        cycleKey: String,
        currency: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        fun cascade(offset: Long, level: Int, delayMillis: Long) {
            val cascadeIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = "CASCADE_REMINDER"
                putExtra("bill_id", billId)
                putExtra("bill_name", billName)
                putExtra("amount", amount)
                putExtra("currency", currency)
                putExtra("days_until_due", daysUntilDue)
                putExtra("is_auto_pay", isAutoPay)
                putExtra("cycle_key", cycleKey)
                putExtra("cascade_level", level)
            }
            val pending = PendingIntent.getBroadcast(
                context, (billId + offset).toInt(), cascadeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + delayMillis, pending)
        }

        cascade(ReminderScheduler.CASCADE_4H_OFFSET, 1, 4 * 60 * 60 * 1000L)
        cascade(ReminderScheduler.CASCADE_24H_OFFSET, 2, 24 * 60 * 60 * 1000L)
    }

    private fun handleCascadeReminder(context: Context, intent: Intent, repo: BillRepository) {
        val billId = intent.getLongExtra("bill_id", -1)
        val cycleKey = intent.getStringExtra("cycle_key").orEmpty()
        val cascadeLevel = intent.getIntExtra("cascade_level", 1)
        if (billId == -1L) return

        runAsync {
            val bill = repo.getBillById(billId) ?: return@runAsync
            val currentCycle = repo.currentCycleFor(bill) ?: return@runAsync
            if (CycleEngine.cycleKey(currentCycle) != cycleKey) return@runAsync

            // Already paid: nothing to escalate.
            if (repo.getPaymentForCycle(billId, cycleKey) != null) return@runAsync

            val daysUntilDue = java.time.temporal.ChronoUnit.DAYS
                .between(LocalDate.now(), currentCycle).toInt()

            if (daysUntilDue < 0) {
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
                    nextDueDate = CycleEngine.dueInstant(currentCycle),
                    currency = bill.currency
                )
            }
        }
    }

    private fun cancelCascade(context: Context, billId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(
            (billId + ReminderScheduler.CASCADE_4H_OFFSET).toInt(),
            (billId + ReminderScheduler.CASCADE_24H_OFFSET).toInt()
        ).forEach { requestCode ->
            val intent = Intent(context, ReminderReceiver::class.java).apply { action = "CASCADE_REMINDER" }
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let(alarmManager::cancel)
        }
    }

    private fun rescheduleAll(context: Context, repo: BillRepository) {
        runAsync {
            ReminderScheduler.scheduleAllReminders(context, repo.getAllBillsList())
        }
    }
}
