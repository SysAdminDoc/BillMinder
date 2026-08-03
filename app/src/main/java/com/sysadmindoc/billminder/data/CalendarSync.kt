package com.sysadmindoc.billminder.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

data class CalendarEventDetails(
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val recurrenceRule: String?
)

object CalendarSync {
    fun details(bill: Bill, dueDate: Long): CalendarEventDetails {
        val start = Calendar.getInstance().apply {
            timeInMillis = dueDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val description = buildString {
            append("BillMinder due date")
            append("\nAmount: ")
            append(CurrencyFormatter.format(bill.amount, bill.currency))
            if (bill.isAutoPay) append("\nAuto-Pay: Yes")
            if (bill.notes.isNotBlank()) append("\n\n${bill.notes}")
        }
        return CalendarEventDetails(
            title = "BillMinder: ${bill.name}",
            description = description,
            startMillis = start.timeInMillis,
            endMillis = end.timeInMillis,
            recurrenceRule = recurrenceRule(bill.recurrence)
        )
    }

    fun buildInsertIntent(bill: Bill, dueDate: Long): Intent {
        val event = details(bill, dueDate)
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.Events.DESCRIPTION, event.description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
            putExtra(CalendarContract.Events.ALL_DAY, 1)
            putExtra(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            event.recurrenceRule?.let { putExtra(CalendarContract.Events.RRULE, it) }
        }
    }

    fun openInsert(context: Context, bill: Bill, dueDate: Long): Boolean = try {
        context.startActivity(
            Intent.createChooser(buildInsertIntent(bill, dueDate), "Add bill to calendar")
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    internal fun recurrenceRule(recurrence: Recurrence): String? = when (recurrence) {
        Recurrence.WEEKLY -> "FREQ=WEEKLY"
        Recurrence.BIWEEKLY -> "FREQ=WEEKLY;INTERVAL=2"
        Recurrence.MONTHLY -> "FREQ=MONTHLY"
        Recurrence.QUARTERLY -> "FREQ=MONTHLY;INTERVAL=3"
        Recurrence.YEARLY -> "FREQ=YEARLY"
        Recurrence.ONE_TIME -> null
    }
}
