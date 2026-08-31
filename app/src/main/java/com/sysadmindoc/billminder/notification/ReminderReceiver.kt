package com.sysadmindoc.billminder.notification

import android.app.AlarmManager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.BillRepository
import com.sysadmindoc.billminder.data.Payment
import com.sysadmindoc.billminder.domain.CycleEngine
import com.sysadmindoc.billminder.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repo = BillRepository(BillDatabase.getDatabase(context))

        when (intent.action) {
            ReminderScheduler.ACTION_REMINDER -> handleReminder(context, intent, repo)
            ReminderScheduler.ACTION_OVERDUE -> handleOverdue(context, intent, repo)
            "MARK_PAID" -> handleMarkPaid(context, intent, repo)
            "SNOOZE" -> handleSnooze(context, intent)
            ReminderScheduler.ACTION_SNOOZED -> handleSnoozedReminder(context, intent)
            "REMINDER_DISMISSED" -> handleReminderDismissed(context, intent, repo)
            ReminderScheduler.ACTION_CASCADE -> handleCascadeReminder(context, intent, repo)
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

    private fun billIdOf(intent: Intent): Long = intent.getLongExtra(ReminderScheduler.EXTRA_BILL_ID, -1L)

    private fun handleReminder(context: Context, intent: Intent, repo: BillRepository) {
        val billId = billIdOf(intent)
        if (billId == -1L) return
        val daysBeforeDue = intent.getIntExtra(ReminderScheduler.EXTRA_DAYS_BEFORE_DUE, 1)
        val cycleKey = intent.getStringExtra(ReminderScheduler.EXTRA_CYCLE_KEY).orEmpty()

        runAsync {
            val bill = repo.getBillById(billId) ?: return@runAsync
            val cycleDate = CycleEngine.parseCycleKey(cycleKey)
                ?: repo.currentCycleFor(bill)
                ?: return@runAsync
            val key = CycleEngine.cycleKey(cycleDate)

            if (repo.getPaymentForCycle(bill.id, key) == null) {
                NotificationHelper.showReminderNotification(
                    context = context,
                    billId = bill.id,
                    billName = bill.name,
                    amount = bill.amount,
                    daysUntilDue = daysBeforeDue,
                    isAutoPay = bill.isAutoPay,
                    nextDueDate = CycleEngine.dueInstant(cycleDate),
                    cycleKey = key,
                    currency = bill.currency
                )
            }

            ReminderScheduler.scheduleReminder(context, bill)
        }
    }

    private fun handleOverdue(context: Context, intent: Intent, repo: BillRepository) {
        val billId = billIdOf(intent)
        if (billId == -1L) return
        val cycleKey = intent.getStringExtra(ReminderScheduler.EXTRA_CYCLE_KEY).orEmpty()

        runAsync {
            val bill = repo.getBillById(billId) ?: return@runAsync
            val cycleDate = CycleEngine.parseCycleKey(cycleKey) ?: return@runAsync
            if (repo.getPaymentForCycle(billId, cycleKey) != null) return@runAsync

            val daysPastDue = ChronoUnit.DAYS.between(cycleDate, LocalDate.now()).toInt()
            if (daysPastDue <= 0) return@runAsync
            NotificationHelper.showOverdueNotification(
                context = context,
                billId = billId,
                billName = bill.name,
                amount = bill.amount,
                daysPastDue = daysPastDue,
                cycleKey = cycleKey,
                currency = bill.currency
            )
            ReminderScheduler.scheduleReminder(context, bill)
        }
    }

    private fun handleMarkPaid(context: Context, intent: Intent, repo: BillRepository) {
        val billId = billIdOf(intent)
        val amount = intent.getDoubleExtra("amount", 0.0)
        val cycleKey = intent.getStringExtra(ReminderScheduler.EXTRA_CYCLE_KEY).orEmpty()
        if (billId == -1L) return

        runAsync {
            val bill = repo.getBillById(billId) ?: return@runAsync
            if (ReminderPrefs.isVacationMode(context) && bill.isAutoPay) {
                ReminderScheduler.scheduleReminder(context, bill)
                return@runAsync
            }
            // Credit the cycle the notification was about, not whatever is oldest right now.
            val cycle = CycleEngine.parseCycleKey(cycleKey)
                ?: repo.currentCycleFor(bill)
                ?: return@runAsync
            repo.insertPayment(
                Payment(
                    billId = billId,
                    amount = if (amount > 0.0) amount else bill.amount,
                    dueDate = CycleEngine.dueInstant(cycle),
                    currency = bill.currency,
                    cycleKey = CycleEngine.cycleKey(cycle)
                )
            )
            NotificationHelper.cancelAll(context, billId)
            cancelCascade(context, billId)
            ReminderScheduler.scheduleReminder(context, bill)
            WidgetUpdater.updateAll(context)
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val billId = billIdOf(intent)
        val billName = intent.getStringExtra("bill_name") ?: return
        val amount = intent.getDoubleExtra("amount", 0.0)
        val daysUntilDue = intent.getIntExtra("days_until_due", 0)
        val isAutoPay = intent.getBooleanExtra("is_auto_pay", false)
        val currency = intent.getStringExtra("currency") ?: "USD"
        val snoozeMinutes = intent.getIntExtra("snooze_minutes", 60)
        val cycleKey = intent.getStringExtra(ReminderScheduler.EXTRA_CYCLE_KEY).orEmpty()

        NotificationHelper.cancelAll(context, billId)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeIntent = ReminderScheduler.alarmIntent(
            context, billId, AlarmSlot.SNOOZED_REMINDER, ReminderScheduler.ACTION_SNOOZED
        ).apply {
            putExtra("bill_name", billName)
            putExtra("amount", amount)
            putExtra("currency", currency)
            putExtra("days_until_due", daysUntilDue)
            putExtra("is_auto_pay", isAutoPay)
            putExtra(ReminderScheduler.EXTRA_CYCLE_KEY, cycleKey)
        }
        val pendingIntent = ReminderScheduler.pendingIntent(
            context, billId, AlarmSlot.SNOOZED_REMINDER, ReminderScheduler.ACTION_SNOOZED, snoozeIntent
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            ReminderScheduler.nowPlusMinutes(snoozeMinutes),
            pendingIntent
        )
    }

    private fun handleSnoozedReminder(context: Context, intent: Intent) {
        val billId = billIdOf(intent)
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
            cycleKey = intent.getStringExtra(ReminderScheduler.EXTRA_CYCLE_KEY).orEmpty(),
            currency = currency
        )
    }

    private fun handleReminderDismissed(context: Context, intent: Intent, repo: BillRepository) {
        val billId = billIdOf(intent)
        if (billId == -1L) return
        val cycleKey = intent.getStringExtra(ReminderScheduler.EXTRA_CYCLE_KEY).orEmpty()

        runAsync {
            val bill = repo.getBillById(billId) ?: return@runAsync
            val cycle = CycleEngine.parseCycleKey(cycleKey)
                ?: repo.currentCycleFor(bill)
                ?: return@runAsync
            val key = CycleEngine.cycleKey(cycle)
            if (repo.getPaymentForCycle(bill.id, key) == null) {
                scheduleCascade(
                    context = context,
                    billId = bill.id,
                    cycleKey = key
                )
            }
        }
    }

    private fun scheduleCascade(context: Context, billId: Long, cycleKey: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        fun cascade(slot: AlarmSlot, level: Int, delayMillis: Long) {
            val intent = ReminderScheduler.alarmIntent(
                context, billId, slot, ReminderScheduler.ACTION_CASCADE
            ).apply {
                putExtra(ReminderScheduler.EXTRA_CYCLE_KEY, cycleKey)
                putExtra("cascade_level", level)
            }
            val pending = ReminderScheduler.pendingIntent(
                context, billId, slot, ReminderScheduler.ACTION_CASCADE, intent
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + delayMillis, pending)
        }

        cascade(AlarmSlot.CASCADE_FOLLOW_UP, 1, 4 * 60 * 60 * 1000L)
        cascade(AlarmSlot.CASCADE_URGENT, 2, 24 * 60 * 60 * 1000L)
    }

    private fun handleCascadeReminder(context: Context, intent: Intent, repo: BillRepository) {
        val billId = billIdOf(intent)
        val cycleKey = intent.getStringExtra(ReminderScheduler.EXTRA_CYCLE_KEY).orEmpty()
        val cascadeLevel = intent.getIntExtra("cascade_level", 1)
        if (billId == -1L) return

        runAsync {
            val bill = repo.getBillById(billId) ?: return@runAsync
            val currentCycle = repo.currentCycleFor(bill) ?: return@runAsync
            if (CycleEngine.cycleKey(currentCycle) != cycleKey) return@runAsync
            if (repo.getPaymentForCycle(billId, cycleKey) != null) return@runAsync

            val daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), currentCycle).toInt()

            if (daysUntilDue < 0) {
                NotificationHelper.showOverdueNotification(
                    context = context,
                    billId = billId,
                    billName = bill.name,
                    amount = bill.amount,
                    daysPastDue = -daysUntilDue,
                    cycleKey = cycleKey,
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
                    cycleKey = cycleKey,
                    currency = bill.currency
                )
            }
        }
    }

    private fun cancelCascade(context: Context, billId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(AlarmSlot.CASCADE_FOLLOW_UP, AlarmSlot.CASCADE_URGENT).forEach { slot ->
            android.app.PendingIntent.getBroadcast(
                context,
                AlarmIds.code(billId, slot),
                ReminderScheduler.alarmIntent(context, billId, slot, ReminderScheduler.ACTION_CASCADE),
                android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
            )?.let { pending ->
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    private fun rescheduleAll(context: Context, repo: BillRepository) {
        runAsync {
            ReminderScheduler.scheduleAllReminders(context, repo.getAllBillsList())
        }
    }
}

