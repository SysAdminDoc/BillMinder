package com.sysadmindoc.billminder.domain

import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.floor

/**
 * Deterministic occurrence generation for bills.
 *
 * Every occurrence is a local date derived from the bill's stored anchor date, so the same bill
 * always yields the same occurrences no matter when the calculation runs. The cycle key is that
 * date in ISO form, which gives each occurrence one stable identity that carries no wall-clock
 * milliseconds and survives timezone changes, reboots, and process restarts.
 */
object CycleEngine {

    /** Upper bound on occurrences returned from a single range query. */
    const val MAX_OCCURRENCES = 1024

    /** How far back [currentCycle] looks for an unpaid occurrence. */
    const val LOOKBACK_MONTHS = 24L

    private val KEY_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun cycleKey(date: LocalDate): String = date.format(KEY_FORMAT)

    fun parseCycleKey(key: String): LocalDate? =
        try {
            LocalDate.parse(key, KEY_FORMAT)
        } catch (error: Exception) {
            null
        }

    /** End of the local day the occurrence falls on. */
    fun dueInstant(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

    fun toLocalDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun cycleKeyForInstant(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        cycleKey(toLocalDate(millis, zone))

    /**
     * The bill's anchor date, which is its first occurrence. Bills written by this version always
     * carry a stored anchor; anything older falls back to a derivation from its creation timestamp
     * so the result stays deterministic.
     */
    fun anchor(bill: Bill, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        if (bill.anchorEpochDay > 0L) {
            LocalDate.ofEpochDay(bill.anchorEpochDay)
        } else {
            deriveAnchor(bill, toLocalDate(bill.createdAt, zone))
        }

    /**
     * Derives an anchor for a bill that has none, using [reference] as the earliest allowed date.
     * The legacy fields keep their original meaning: [Bill.dueDay] is a day of the week for weekly
     * and biweekly rules (1 = Sunday, matching `Calendar.DAY_OF_WEEK`) and a day of the month
     * otherwise.
     */
    fun deriveAnchor(bill: Bill, reference: LocalDate): LocalDate = when (bill.recurrence) {
        Recurrence.WEEKLY, Recurrence.BIWEEKLY -> {
            val target = bill.dueDay.coerceIn(1, 7)
            var candidate = reference
            while (calendarDayOfWeek(candidate) != target) {
                candidate = candidate.plusDays(1)
            }
            candidate
        }

        Recurrence.ONE_TIME -> {
            val year = bill.dueYear ?: reference.year
            val month = (bill.dueMonth ?: (reference.monthValue - 1)).coerceIn(0, 11) + 1
            dayIn(YearMonth.of(year, month), bill.dueDay)
        }

        Recurrence.YEARLY -> {
            val month = (bill.dueMonth ?: (reference.monthValue - 1)).coerceIn(0, 11) + 1
            val year = bill.dueYear ?: reference.year
            val candidate = dayIn(YearMonth.of(year, month), bill.dueDay)
            if (candidate.isBefore(reference)) dayIn(YearMonth.of(year + 1, month), bill.dueDay) else candidate
        }

        Recurrence.MONTHLY, Recurrence.QUARTERLY -> {
            val candidate = dayIn(YearMonth.from(reference), bill.dueDay)
            if (candidate.isBefore(reference)) {
                dayIn(YearMonth.from(reference).plusMonths(1), bill.dueDay)
            } else {
                candidate
            }
        }
    }

    /** The first occurrence on or after [from], or null when the rule has already finished. */
    fun occurrenceOnOrAfter(
        bill: Bill,
        from: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate? {
        val start = anchor(bill, zone)
        if (!start.isBefore(from)) return start

        return when (bill.recurrence) {
            Recurrence.ONE_TIME -> null
            Recurrence.WEEKLY -> weeklyOnOrAfter(start, from, 7L)
            Recurrence.BIWEEKLY -> weeklyOnOrAfter(start, from, 14L)
            Recurrence.MONTHLY -> monthlyOnOrAfter(bill, start, from, 1L)
            Recurrence.QUARTERLY -> monthlyOnOrAfter(bill, start, from, 3L)
            Recurrence.YEARLY -> monthlyOnOrAfter(bill, start, from, 12L)
        }
    }

    /** The first occurrence strictly after [after]. */
    fun occurrenceAfter(
        bill: Bill,
        after: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate? = occurrenceOnOrAfter(bill, after.plusDays(1), zone)

    /** The last occurrence on or before [until], or null when the rule starts later than that. */
    fun occurrenceOnOrBefore(
        bill: Bill,
        until: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate? {
        val start = anchor(bill, zone)
        if (start.isAfter(until)) return null
        if (bill.recurrence == Recurrence.ONE_TIME) return start

        return when (bill.recurrence) {
            Recurrence.WEEKLY -> weeklyOnOrBefore(start, until, 7L)
            Recurrence.BIWEEKLY -> weeklyOnOrBefore(start, until, 14L)
            Recurrence.MONTHLY -> monthlyOnOrBefore(bill, start, until, 1L)
            Recurrence.QUARTERLY -> monthlyOnOrBefore(bill, start, until, 3L)
            Recurrence.YEARLY -> monthlyOnOrBefore(bill, start, until, 12L)
            Recurrence.ONE_TIME -> start
        }
    }

    /** Whichever occurrence sits closest to [date], preferring the earlier one on a tie. */
    fun nearestOccurrence(
        bill: Bill,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate? {
        val before = occurrenceOnOrBefore(bill, date, zone)
        val after = occurrenceOnOrAfter(bill, date, zone)
        return when {
            before == null -> after
            after == null -> before
            ChronoUnit.DAYS.between(before, date) <= ChronoUnit.DAYS.between(date, after) -> before
            else -> after
        }
    }

    /** Every occurrence between [start] and [endInclusive], capped at [MAX_OCCURRENCES]. */
    fun occurrencesInRange(
        bill: Bill,
        start: LocalDate,
        endInclusive: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<LocalDate> {
        if (endInclusive.isBefore(start)) return emptyList()
        val result = mutableListOf<LocalDate>()
        var current = occurrenceOnOrAfter(bill, start, zone)
        while (current != null && !current.isAfter(endInclusive) && result.size < MAX_OCCURRENCES) {
            result.add(current)
            val next = occurrenceAfter(bill, current, zone)
            if (next == null || !next.isAfter(current)) break
            current = next
        }
        return result
    }

    /**
     * The occurrence the user is currently looking at and acting on.
     *
     * It is the oldest unpaid occurrence up to today, so an unpaid cycle stays current and overdue
     * instead of rolling forward. When everything up to today is settled, it is the most recent
     * occurrence, which keeps the paid state visible until the next one comes around rather than
     * jumping straight to a future date the user has not been asked for yet. A bill whose first
     * occurrence is still ahead reports that occurrence.
     */
    fun currentCycle(
        bill: Bill,
        today: LocalDate,
        isPaid: (String) -> Boolean,
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate? {
        val start = maxOf(anchor(bill, zone), today.minusMonths(LOOKBACK_MONTHS))
        occurrencesInRange(bill, start, today, zone)
            .firstOrNull { !isPaid(cycleKey(it)) }
            ?.let { return it }
        // Nothing outstanding: stay on the last occurrence (paid, or older than the lookback
        // window) so the bill keeps a visible state, and only look ahead when there is none.
        return occurrenceOnOrBefore(bill, today, zone) ?: occurrenceOnOrAfter(bill, today, zone)
    }

    /** `Calendar.DAY_OF_WEEK` value for [date] (Sunday = 1 through Saturday = 7). */
    fun calendarDayOfWeek(date: LocalDate): Int = (date.dayOfWeek.value % 7) + 1

    /**
     * The legacy day field that matches [anchorDate] for [recurrence], so exports and older
     * consumers keep reading a sensible value. A [requestedDay] past the end of the anchor's month
     * survives, because the user asked for a day that month simply does not have.
     */
    fun legacyDueDay(recurrence: Recurrence, anchorDate: LocalDate, requestedDay: Int): Int =
        when (recurrence) {
            Recurrence.WEEKLY, Recurrence.BIWEEKLY -> calendarDayOfWeek(anchorDate)
            else -> if (requestedDay > anchorDate.dayOfMonth &&
                anchorDate.dayOfMonth == anchorDate.lengthOfMonth()
            ) {
                requestedDay.coerceAtMost(31)
            } else {
                anchorDate.dayOfMonth
            }
        }

    /**
     * Fills in [Bill.anchorEpochDay] and realigns the legacy day fields with it. Every write path
     * runs through this, so no bill reaches the database without a stored anchor.
     */
    fun normalize(bill: Bill, zone: ZoneId = ZoneId.systemDefault()): Bill {
        val anchorDate = if (bill.anchorEpochDay > 0L) {
            LocalDate.ofEpochDay(bill.anchorEpochDay)
        } else {
            deriveAnchor(bill, toLocalDate(bill.createdAt, zone))
        }
        return bill.copy(
            anchorEpochDay = anchorDate.toEpochDay(),
            dueDay = legacyDueDay(bill.recurrence, anchorDate, bill.dueDay),
            dueMonth = anchorDate.monthValue - 1,
            dueYear = anchorDate.year
        )
    }

    private fun monthlyOccurrence(bill: Bill, start: LocalDate, monthsToAdd: Long): LocalDate =
        dayIn(YearMonth.from(start).plusMonths(monthsToAdd), intendedDayOfMonth(bill, start))

    private fun weeklyOnOrAfter(start: LocalDate, from: LocalDate, stepDays: Long): LocalDate {
        val gap = ChronoUnit.DAYS.between(start, from)
        val steps = ceilDiv(gap, stepDays).coerceAtLeast(0L)
        return start.plusDays(steps * stepDays)
    }

    private fun weeklyOnOrBefore(start: LocalDate, until: LocalDate, stepDays: Long): LocalDate {
        val gap = ChronoUnit.DAYS.between(start, until).coerceAtLeast(0L)
        return start.plusDays((gap / stepDays) * stepDays)
    }

    private fun monthlyOnOrBefore(
        bill: Bill,
        start: LocalDate,
        until: LocalDate,
        stepMonths: Long
    ): LocalDate {
        val day = intendedDayOfMonth(bill, start)
        val startMonth = YearMonth.from(start)
        val monthGap = ChronoUnit.MONTHS.between(startMonth, YearMonth.from(until))
        var steps = (monthGap / stepMonths).coerceAtLeast(0L)
        var candidate = dayIn(startMonth.plusMonths(steps * stepMonths), day)
        while (candidate.isAfter(until) && steps > 0L) {
            steps -= 1
            candidate = dayIn(startMonth.plusMonths(steps * stepMonths), day)
        }
        return candidate
    }

    private fun monthlyOnOrAfter(
        bill: Bill,
        start: LocalDate,
        from: LocalDate,
        stepMonths: Long
    ): LocalDate {
        val day = intendedDayOfMonth(bill, start)
        val startMonth = YearMonth.from(start)
        val monthGap = ChronoUnit.MONTHS.between(startMonth, YearMonth.from(from))
        var steps = floor(monthGap.toDouble() / stepMonths).toLong().coerceAtLeast(0L)
        var candidate = dayIn(startMonth.plusMonths(steps * stepMonths), day)
        while (candidate.isBefore(from)) {
            steps += 1
            candidate = dayIn(startMonth.plusMonths(steps * stepMonths), day)
        }
        return candidate
    }

    /**
     * The day of the month the user asked for. The anchor's own day can have been clamped by a
     * short month, so the larger of the two wins and every later month clamps from that intent.
     */
    private fun intendedDayOfMonth(bill: Bill, start: LocalDate): Int =
        when (bill.recurrence) {
            Recurrence.WEEKLY, Recurrence.BIWEEKLY -> start.dayOfMonth
            else -> maxOf(start.dayOfMonth, bill.dueDay.coerceIn(1, 31))
        }

    private fun dayIn(month: YearMonth, day: Int): LocalDate =
        month.atDay(day.coerceIn(1, 31).coerceAtMost(month.lengthOfMonth()))

    private fun ceilDiv(value: Long, divisor: Long): Long =
        if (value <= 0L) 0L else (value + divisor - 1) / divisor
}
