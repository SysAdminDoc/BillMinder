package com.sysadmindoc.billminder.data

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidayCalendarTest {
    @Test
    fun observedChristmasOnSaturdayMovesPastObservedFriday() {
        val christmas = dateMillis(2021, Calendar.DECEMBER, 25)

        assertTrue(HolidayCalendar.isHoliday(calendar(2021, Calendar.DECEMBER, 24)))
        assertEquals(dateMillis(2021, Calendar.DECEMBER, 23), dateOnly(HolidayCalendar.previousBusinessDay(christmas)))
    }

    @Test
    fun observedIndependenceDayOnSundayMovesToMonday() {
        assertTrue(HolidayCalendar.isHoliday(calendar(2021, Calendar.JULY, 5)))
        assertEquals(dateMillis(2021, Calendar.JULY, 2), dateOnly(
            HolidayCalendar.previousBusinessDay(dateMillis(2021, Calendar.JULY, 4))
        ))
    }

    @Test
    fun regularWeekdayIsUnchanged() {
        val weekday = dateMillis(2026, Calendar.AUGUST, 3)
        assertEquals(weekday, dateOnly(HolidayCalendar.previousBusinessDay(weekday)))
    }

    private fun calendar(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }

    private fun dateMillis(year: Int, month: Int, day: Int): Long = calendar(year, month, day).timeInMillis

    private fun dateOnly(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
