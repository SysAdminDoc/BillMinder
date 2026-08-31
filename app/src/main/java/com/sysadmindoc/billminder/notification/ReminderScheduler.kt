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

    const val ACTION_REMINDER = "BILL_REMINDER"
    const val ACTION_OVERDUE = "OVERDUE_REMINDER"
    const val ACTION_SNOOZED = "SNOOZED_REMINDER"
    const val ACTION_CASCADE = "CASCADE_REMINDER"

    const val EXTRA_BILL_ID = "bill_id"
    const val EXTRA_CYCLE_KEY = "cycle_key"
    const val EXTRA_DAYS_BEFORE_DUE = "days_before_due"

    /** Hour of the local day reminders fire at. */
    private const val REMINDER_HOUR = 9

    /**
     * Rebuilds every alarm for [bill]. The primary reminder, the optional second reminder, and the
     * overdue alarm each own a distinct identity, so they coexist rather than overwriting each
     * other, and rescheduling replaces exactly the alarms it just cancelled.
     */
    fun scheduleReminder(context: Context, bill: Bill) {
        cancelReminder(context, bill.id)

        if (ReminderPrefs.isVacationMode(context) && bill.isAutoPay) return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        var cycle = CycleEngine.occurrenceOnOrAfter(bill, today, zone) ?: return
        var reminderTime = reminderTimeFor(cycle, bill.reminderTiming.days, zone)

        // If the reminder time already passed, move to the next occurrence.
        if (reminderTime <= System.currentTimeMillis()) {
            val following = CycleEngine.occurrenceAfter(bill, cycle, zone)
            if (following != null) {
                cycle = following
                reminderTime = reminderTimeFor(cycle, bill.reminderTiming.days, zone)
            }
        }

        val cycleKey = CycleEngine.cycleKey(cycle)
        schedule(
            context = context,
            billId = bill.id,
            slot = AlarmSlot.PRIMARY_REMINDER,
            action = ACTION_REMINDER,
            cycleKey = cycleKey,
            triggerAtMillis = reminderTime,
            daysBeforeDue = bill.reminderTiming.days,
            exact = true
        )

        bill.secondReminderTiming?.let { second ->
            val secondTime = reminderTimeFor(cycle, second.days, zone)
            if (secondTime > System.currentTimeMillis()) {
                schedule(
                    context = context,
                    billId = bill.id,
                    slot = AlarmSlot.SECOND_REMINDER,
                    action = ACTION_REMINDER,
                    cycleKey = cycleKey,
                    triggerAtMillis = secondTime,
                    daysBeforeDue = second.days,
                    exact = true
                )
            }
        }

        // Overdue check the morning after the occurrence was due.
        val overdueTime = atReminderHour(cycle.plusDays(1), zone)
        if (overdueTime > System.currentTimeMillis()) {
            schedule(
                context = context,
                billId = bill.id,
                slot = AlarmSlot.OVERDUE_ALARM,
                action = ACTION_OVERDUE,
                cycleKey = cycleKey,
                triggerAtMillis = overdueTime,
                daysBeforeDue = 0,
                exact = false
            )
        }
    }

    fun cancelReminder(context: Context, billId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(
            AlarmSlot.PRIMARY_REMINDER to ACTION_REMINDER,
            AlarmSlot.SECOND_REMINDER to ACTION_REMINDER,
            AlarmSlot.OVERDUE_ALARM to ACTION_OVERDUE,
            AlarmSlot.SNOOZED_REMINDER to ACTION_SNOOZED,
            AlarmSlot.CASCADE_FOLLOW_UP to ACTION_CASCADE,
            AlarmSlot.CASCADE_URGENT to ACTION_CASCADE
        ).forEach { (slot, action) ->
            existingPendingIntent(context, billId, slot, action)?.let { pending ->
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    fun scheduleAllReminders(context: Context, bills: List<Bill>) {
        bills.filter { it.isEnabled }.forEach { scheduleReminder(context, it) }
    }

    /** Builds the alarm intent for a bill and slot. Identity lives in the action plus the data URI. */
    fun alarmIntent(context: Context, billId: Long, slot: AlarmSlot, action: String): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            data = AlarmIds.uri(billId, slot)
            putExtra(EXTRA_BILL_ID, billId)
        }

    fun pendingIntent(
        context: Context,
        billId: Long,
        slot: AlarmSlot,
        action: String,
        intent: Intent = alarmIntent(context, billId, slot, action)
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        AlarmIds.code(billId, slot),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun existingPendingIntent(
        context: Context,
        billId: Long,
        slot: AlarmSlot,
        action: String
    ): PendingIntent? = PendingIntent.getBroadcast(
        context,
        AlarmIds.code(billId, slot),
        alarmIntent(context, billId, slot, action),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )

    private fun schedule(
        context: Context,
        billId: Long,
        slot: AlarmSlot,
        action: String,
        cycleKey: String,
        triggerAtMillis: Long,
        daysBeforeDue: Int,
        exact: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = alarmIntent(context, billId, slot, action).apply {
            putExtra(EXTRA_CYCLE_KEY, cycleKey)
            putExtra(EXTRA_DAYS_BEFORE_DUE, daysBeforeDue)
        }
        val pending = pendingIntent(context, billId, slot, action, intent)

        val canBeExact = exact && (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            )
        if (canBeExact) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, pending), pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    /** Due instant of the first occurrence on or after [today]; falls back to the anchor. */
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

    /**
     * When a reminder for [cycle] should fire: [daysBeforeDue] ahead of the last business day on or
     * before the occurrence, at [REMINDER_HOUR] local time.
     */
    fun reminderTimeFor(cycle: LocalDate, daysBeforeDue: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val businessDay = CycleEngine.toLocalDate(
            HolidayCalendar.previousBusinessDay(CycleEngine.dueInstant(cycle, zone)),
            zone
        )
        return atReminderHour(businessDay.minusDays(daysBeforeDue.toLong()), zone)
    }

    private fun atReminderHour(date: LocalDate, zone: ZoneId): Long =
        date.atTime(REMINDER_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

    /** Kept for the notification snooze path, which still works in wall-clock minutes. */
    internal fun nowPlusMinutes(minutes: Int): Long =
        Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }.timeInMillis
}
