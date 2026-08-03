package com.sysadmindoc.billminder.data

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSyncTest {
    @Test
    fun recurringBillsUseMatchingCalendarRules() {
        assertEquals("FREQ=WEEKLY", CalendarSync.recurrenceRule(Recurrence.WEEKLY))
        assertEquals("FREQ=WEEKLY;INTERVAL=2", CalendarSync.recurrenceRule(Recurrence.BIWEEKLY))
        assertEquals("FREQ=MONTHLY", CalendarSync.recurrenceRule(Recurrence.MONTHLY))
        assertEquals("FREQ=MONTHLY;INTERVAL=3", CalendarSync.recurrenceRule(Recurrence.QUARTERLY))
        assertEquals("FREQ=YEARLY", CalendarSync.recurrenceRule(Recurrence.YEARLY))
        assertNull(CalendarSync.recurrenceRule(Recurrence.ONE_TIME))
    }

    @Test
    fun eventIsAnAllDayDateWithBillContext() {
        val dueDate = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.AUGUST, 3, 23, 59, 59)
        }.timeInMillis
        val bill = Bill(
            name = "Internet",
            amount = 79.99,
            dueDay = 3,
            recurrence = Recurrence.MONTHLY,
            isAutoPay = true,
            notes = "Check annual rate"
        )

        val event = CalendarSync.details(bill, dueDate)
        val start = Calendar.getInstance().apply { timeInMillis = event.startMillis }
        val end = Calendar.getInstance().apply { timeInMillis = event.endMillis }

        assertEquals("BillMinder: Internet", event.title)
        assertTrue(event.description.contains("$79.99"))
        assertTrue(event.description.contains("Auto-Pay: Yes"))
        assertEquals(0, start.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, start.get(Calendar.MINUTE))
        assertEquals(Calendar.AUGUST, start.get(Calendar.MONTH))
        assertEquals(Calendar.AUGUST, end.get(Calendar.MONTH))
        assertEquals(4, end.get(Calendar.DAY_OF_MONTH))
        assertEquals("FREQ=MONTHLY", event.recurrenceRule)
    }
}
