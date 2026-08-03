package com.sysadmindoc.billminder.data

import java.util.Calendar

/**
 * US Federal Holiday calendar with weekend awareness.
 * Used to find the previous business day when a bill due date
 * falls on a weekend or holiday.
 */
object HolidayCalendar {

    /**
     * Returns the list of US federal holiday dates (month, day) for a given year.
     * Fixed-date holidays include their observed weekday when the actual holiday
     * falls on a weekend. The adjacent-year lookup handles observed New Year's
     * and Christmas dates that cross a calendar-year boundary.
     */
    fun getFederalHolidays(year: Int): List<Pair<Int, Int>> {
        val holidays = linkedSetOf<Pair<Int, Int>>()
        for (holidayYear in (year - 1)..(year + 1)) {
            val fixedDates = mutableListOf(
                newDate(holidayYear, Calendar.JANUARY, 1),
                newDate(holidayYear, Calendar.JULY, 4),
                newDate(holidayYear, Calendar.NOVEMBER, 11),
                newDate(holidayYear, Calendar.DECEMBER, 25)
            )
            if (holidayYear >= 2021) {
                fixedDates += newDate(holidayYear, Calendar.JUNE, 19)
            }

            fixedDates.forEach { actual ->
                addIfInYear(holidays, actual, year)
                val observed = (actual.clone() as Calendar).apply {
                    when (get(Calendar.DAY_OF_WEEK)) {
                        Calendar.SATURDAY -> add(Calendar.DAY_OF_MONTH, -1)
                        Calendar.SUNDAY -> add(Calendar.DAY_OF_MONTH, 1)
                    }
                }
                addIfInYear(holidays, observed, year)
            }

            addIfInYear(
                holidays,
                newDate(holidayYear, Calendar.JANUARY, nthWeekday(holidayYear, Calendar.JANUARY, Calendar.MONDAY, 3)),
                year
            )
            addIfInYear(
                holidays,
                newDate(holidayYear, Calendar.FEBRUARY, nthWeekday(holidayYear, Calendar.FEBRUARY, Calendar.MONDAY, 3)),
                year
            )
            addIfInYear(
                holidays,
                newDate(holidayYear, Calendar.MAY, lastWeekday(holidayYear, Calendar.MAY, Calendar.MONDAY)),
                year
            )
            addIfInYear(
                holidays,
                newDate(holidayYear, Calendar.SEPTEMBER, nthWeekday(holidayYear, Calendar.SEPTEMBER, Calendar.MONDAY, 1)),
                year
            )
            addIfInYear(
                holidays,
                newDate(holidayYear, Calendar.OCTOBER, nthWeekday(holidayYear, Calendar.OCTOBER, Calendar.MONDAY, 2)),
                year
            )
            addIfInYear(
                holidays,
                newDate(holidayYear, Calendar.NOVEMBER, nthWeekday(holidayYear, Calendar.NOVEMBER, Calendar.THURSDAY, 4)),
                year
            )
        }
        return holidays.toList()
    }

    /**
     * Check if a given date is a weekend (Saturday or Sunday).
     */
    fun isWeekend(cal: Calendar): Boolean {
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }

    /**
     * Check if a given date is a US federal holiday.
     */
    fun isHoliday(cal: Calendar): Boolean {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return getFederalHolidays(year).any { it.first == month && it.second == day }
    }

    /**
     * Check if a date is a non-business day (weekend or holiday).
     */
    fun isNonBusinessDay(cal: Calendar): Boolean = isWeekend(cal) || isHoliday(cal)

    /**
     * Returns the previous business day if the given date falls on a weekend or holiday.
     * Returns the same date if it's already a business day.
     */
    fun previousBusinessDay(timeInMillis: Long): Long {
        val cal = Calendar.getInstance().apply { this.timeInMillis = timeInMillis }
        while (isNonBusinessDay(cal)) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return cal.timeInMillis
    }

    /**
     * Returns a human-readable note if the due date falls on a non-business day.
     * e.g. "Due date falls on Saturday. Previous business day: Fri, Dec 22"
     */
    fun getHolidayNote(dueDateMillis: Long): String? {
        val dueCal = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
        if (!isNonBusinessDay(dueCal)) return null

        val reason = when {
            isHoliday(dueCal) && isWeekend(dueCal) -> "a holiday weekend"
            isHoliday(dueCal) -> "a holiday"
            else -> {
                if (dueCal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) "Saturday"
                else "Sunday"
            }
        }

        val prevBiz = previousBusinessDay(dueDateMillis)
        val prevCal = Calendar.getInstance().apply { timeInMillis = prevBiz }
        val fmt = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
        return "Due date falls on $reason. Pay by ${fmt.format(prevCal.time)}"
    }

    /**
     * Find the nth occurrence of a weekday in a month.
     */
    private fun nthWeekday(year: Int, month: Int, dayOfWeek: Int, n: Int): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        var count = 0
        while (true) {
            if (cal.get(Calendar.DAY_OF_WEEK) == dayOfWeek) {
                count++
                if (count == n) return cal.get(Calendar.DAY_OF_MONTH)
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    /**
     * Find the last occurrence of a weekday in a month.
     */
    private fun lastWeekday(year: Int, month: Int, dayOfWeek: Int): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    private fun newDate(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }

    private fun addIfInYear(target: MutableSet<Pair<Int, Int>>, date: Calendar, year: Int) {
        if (date.get(Calendar.YEAR) == year) {
            target += date.get(Calendar.MONTH) to date.get(Calendar.DAY_OF_MONTH)
        }
    }
}
