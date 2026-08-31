package com.sysadmindoc.billminder.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.HolidayCalendar
import com.sysadmindoc.billminder.domain.CycleEngine
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

object ReminderScheduler {

    fun scheduleReminder(context: Context, bill: Bill) {
        cancelReminder(context, bill.id)

        if (ReminderPrefs.isVacationMode(context) && bill.isAutoPay) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        var cycle = CycleEngine.occurrenceOnOrAfter(bill, today, zone) ?: return
        var reminderTime = getReminderTime(CycleEngine.dueInstant(cycle, zone), bill.reminderTiming.days)

        // If the reminder time already passed, move to the next occurrence.
        if (reminderTime.timeInMillis <= System.currentTimeMillis()) {
            val following = CycleEngine.occurrenceAfter(bill, cycle, zone)
            if (following != null) {
                cycle = following
                reminderTime = getReminderTime(CycleEngine.dueInstant(cycle, zone), bill.reminderTiming.days)
            }
        }

        val cycleKey = CycleEngine.cycleKey(cycle)
        scheduleExactAlarm(context, bill.id, cycleKey, reminderTime.timeInMillis, bill.reminderTiming.days)

        bill.secondReminderTiming?.let { second ->
            val secondTime = getReminderTime(CycleEngine.dueInstant(cycle, zone), second.days)
            if (secondTime.timeInMillis > System.currentTimeMillis()) {
                scheduleExactAlarm(context, bill.id + SECOND_REMINDER_OFFSET, cycleKey, secondTime.timeInMillis, second.days)
            }
        }
    }

    private fun scheduleExactAlarm(
        context: Context,
        requestCode: Long,
        cycleKey: String,
        triggerAtMillis: Long,
        daysBeforeDue: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "BILL_REMINDER"
            putExtra("request_code", requestCode)
            putExtra("cycle_key", cycleKey)
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
        listOf(
            billId to "BILL_REMINDER",
            (billId + SECOND_REMINDER_OFFSET) to "BILL_REMINDER",
            (billId + SNOOZE_OFFSET) to "SNOOZED_REMINDER",
            (billId + CASCADE_4H_OFFSET) to "CASCADE_REMINDER",
            (billId + CASCADE_24H_OFFSET) to "CASCADE_REMINDER"
        ).forEach { (code, action) ->
            val intent = Intent(context, ReminderReceiver::class.java).apply { this.action = action }
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

    /** Due instant of the first occurrence on or after [today]; falls back to the last occurrence. */
    fun getNextDueDate(
        bill: Bill,
        today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val date = CycleEngine.occurrenceOnOrAfter(bill, today, zone) ?: CycleEngine.anchor(bill, zone)
        return CycleEngine.dueInstant(date, zone)
    }

    fun getNextDueDateAfter(
        bill: Bill,
        afterMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val after = CycleEngine.toLocalDate(afterMillis, zone)
        val next = CycleEngine.occurrenceAfter(bill, after, zone) ?: return null
        return CycleEngine.dueInstant(next, zone)
    }

    const val SECOND_REMINDER_OFFSET = 50_000L
    const val SNOOZE_OFFSET = 60_000L
    const val CASCADE_4H_OFFSET = 70_000L
    const val CASCADE_24H_OFFSET = 80_000L

    private fun getReminderTime(dueDate: Long, daysBeforeDue: Int): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = HolidayCalendar.previousBusinessDay(dueDate)
            add(Calendar.DAY_OF_MONTH, -daysBeforeDue)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
