package com.sysadmindoc.billminder.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.Recurrence
import java.util.Calendar

object ReminderScheduler {

    fun scheduleReminder(context: Context, bill: Bill) {
        cancelReminder(context, bill.id)

        val nextDueDate = getNextDueDate(bill)
        val reminderTime = Calendar.getInstance().apply {
            timeInMillis = nextDueDate
            add(Calendar.DAY_OF_MONTH, -bill.reminderTiming.days)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If reminder time already passed, schedule for next occurrence
        if (reminderTime.timeInMillis <= System.currentTimeMillis()) {
            val nextNext = getNextDueDateAfter(bill, nextDueDate)
            if (nextNext != null) {
                reminderTime.timeInMillis = nextNext
                reminderTime.add(Calendar.DAY_OF_MONTH, -bill.reminderTiming.days)
                reminderTime.set(Calendar.HOUR_OF_DAY, 9)
                reminderTime.set(Calendar.MINUTE, 0)
                reminderTime.set(Calendar.SECOND, 0)
            }
        }

        scheduleExactAlarm(context, bill.id, reminderTime.timeInMillis, bill.reminderTiming.days)

        // Schedule second reminder if set
        bill.secondReminderTiming?.let { second ->
            val secondTime = Calendar.getInstance().apply {
                timeInMillis = nextDueDate
                add(Calendar.DAY_OF_MONTH, -second.days)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (secondTime.timeInMillis > System.currentTimeMillis()) {
                scheduleExactAlarm(context, bill.id + 50000, secondTime.timeInMillis, second.days)
            }
        }
    }

    private fun scheduleExactAlarm(context: Context, requestCode: Long, triggerAtMillis: Long, daysBeforeDue: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "BILL_REMINDER"
            putExtra("request_code", requestCode)
            putExtra("days_before_due", daysBeforeDue)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent),
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        } else {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent),
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, billId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(billId, billId + 50000).forEach { code ->
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, code.toInt(), intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }
    }

    fun scheduleAllReminders(context: Context, bills: List<Bill>) {
        bills.filter { it.isEnabled }.forEach { scheduleReminder(context, it) }
    }

    fun getNextDueDate(bill: Bill): Long {
        val now = Calendar.getInstance()
        val due = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, bill.dueDay.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }

        return when (bill.recurrence) {
            Recurrence.ONE_TIME -> {
                Calendar.getInstance().apply {
                    set(Calendar.YEAR, bill.dueYear ?: now.get(Calendar.YEAR))
                    set(Calendar.MONTH, (bill.dueMonth ?: now.get(Calendar.MONTH)))
                    set(Calendar.DAY_OF_MONTH, bill.dueDay.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                }.timeInMillis
            }
            Recurrence.WEEKLY -> {
                due.apply {
                    set(Calendar.DAY_OF_WEEK, bill.dueDay.coerceIn(1, 7))
                    if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
                }.timeInMillis
            }
            Recurrence.BIWEEKLY -> {
                due.apply {
                    set(Calendar.DAY_OF_WEEK, bill.dueDay.coerceIn(1, 7))
                    if (before(now)) add(Calendar.WEEK_OF_YEAR, 2)
                }.timeInMillis
            }
            Recurrence.MONTHLY -> {
                if (due.before(now)) due.add(Calendar.MONTH, 1)
                due.set(Calendar.DAY_OF_MONTH, bill.dueDay.coerceAtMost(due.getActualMaximum(Calendar.DAY_OF_MONTH)))
                due.timeInMillis
            }
            Recurrence.QUARTERLY -> {
                if (due.before(now)) due.add(Calendar.MONTH, 3)
                due.set(Calendar.DAY_OF_MONTH, bill.dueDay.coerceAtMost(due.getActualMaximum(Calendar.DAY_OF_MONTH)))
                due.timeInMillis
            }
            Recurrence.YEARLY -> {
                due.apply {
                    set(Calendar.MONTH, bill.dueMonth ?: 0)
                    set(Calendar.DAY_OF_MONTH, bill.dueDay.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
                    if (before(now)) add(Calendar.YEAR, 1)
                }.timeInMillis
            }
        }
    }

    private fun getNextDueDateAfter(bill: Bill, afterMillis: Long): Long? {
        if (bill.recurrence == Recurrence.ONE_TIME) return null
        val after = Calendar.getInstance().apply { timeInMillis = afterMillis }
        return when (bill.recurrence) {
            Recurrence.WEEKLY -> {
                after.add(Calendar.WEEK_OF_YEAR, 1)
                after.timeInMillis
            }
            Recurrence.BIWEEKLY -> {
                after.add(Calendar.WEEK_OF_YEAR, 2)
                after.timeInMillis
            }
            Recurrence.MONTHLY -> {
                after.add(Calendar.MONTH, 1)
                after.set(Calendar.DAY_OF_MONTH, bill.dueDay.coerceAtMost(after.getActualMaximum(Calendar.DAY_OF_MONTH)))
                after.timeInMillis
            }
            Recurrence.QUARTERLY -> {
                after.add(Calendar.MONTH, 3)
                after.timeInMillis
            }
            Recurrence.YEARLY -> {
                after.add(Calendar.YEAR, 1)
                after.timeInMillis
            }
            else -> null
        }
    }
}
