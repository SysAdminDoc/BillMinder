package com.sysadmindoc.billminder.domain

import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CycleEngineTest {

    private val utc = ZoneId.of("UTC")

    private fun bill(
        recurrence: Recurrence,
        anchor: String,
        dueDay: Int = LocalDate.parse(anchor).dayOfMonth,
        id: Long = 1L
    ) = Bill(
        id = id,
        name = "Test",
        amount = 10.0,
        dueDay = dueDay,
        recurrence = recurrence,
        anchorEpochDay = LocalDate.parse(anchor).toEpochDay()
    )

    private fun keys(bill: Bill, from: String, to: String, zone: ZoneId = utc): List<String> =
        CycleEngine.occurrencesInRange(bill, LocalDate.parse(from), LocalDate.parse(to), zone)
            .map { CycleEngine.cycleKey(it) }

    @Test
    fun `one time bill has a single occurrence`() {
        val b = bill(Recurrence.ONE_TIME, "2026-03-15")
        assertEquals(listOf("2026-03-15"), keys(b, "2026-01-01", "2027-12-31"))
        assertNull(CycleEngine.occurrenceAfter(b, LocalDate.parse("2026-03-15"), utc))
    }

    @Test
    fun `weekly repeats every seven days from the anchor`() {
        val b = bill(Recurrence.WEEKLY, "2026-01-05")
        assertEquals(
            listOf("2026-01-05", "2026-01-12", "2026-01-19", "2026-01-26"),
            keys(b, "2026-01-01", "2026-01-31")
        )
    }

    @Test
    fun `biweekly keeps its phase instead of following the current date`() {
        val b = bill(Recurrence.BIWEEKLY, "2026-01-05")
        assertEquals(
            listOf("2026-03-02", "2026-03-16", "2026-03-30"),
            keys(b, "2026-03-01", "2026-03-31")
        )
    }

    @Test
    fun `quarterly steps three months from the anchor month`() {
        val b = bill(Recurrence.QUARTERLY, "2026-02-10")
        assertEquals(
            listOf("2026-02-10", "2026-05-10", "2026-08-10", "2026-11-10"),
            keys(b, "2026-01-01", "2026-12-31")
        )
    }

    @Test
    fun `yearly repeats on the same month and day`() {
        val b = bill(Recurrence.YEARLY, "2026-07-04")
        assertEquals(
            listOf("2026-07-04", "2027-07-04", "2028-07-04"),
            keys(b, "2026-01-01", "2028-12-31")
        )
    }

    @Test
    fun `day 31 clamps to short months and returns to 31 afterwards`() {
        val b = bill(Recurrence.MONTHLY, "2026-01-31", dueDay = 31)
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31"),
            keys(b, "2026-01-01", "2026-05-31")
        )
    }

    @Test
    fun `day 29 lands on leap day in a leap year`() {
        val b = bill(Recurrence.MONTHLY, "2028-01-29", dueDay = 29)
        assertEquals(
            listOf("2028-01-29", "2028-02-29", "2028-03-29"),
            keys(b, "2028-01-01", "2028-03-31")
        )
    }

    @Test
    fun `yearly leap day falls back to the 28th in common years`() {
        val b = bill(Recurrence.YEARLY, "2028-02-29", dueDay = 29)
        assertEquals(
            listOf("2028-02-29", "2029-02-28", "2030-02-28", "2031-02-28", "2032-02-29"),
            keys(b, "2028-01-01", "2032-12-31")
        )
    }

    @Test
    fun `repeated calculation returns the same occurrence`() {
        val b = bill(Recurrence.MONTHLY, "2026-06-15")
        val first = CycleEngine.occurrenceOnOrAfter(b, LocalDate.parse("2026-08-02"), utc)
        val second = CycleEngine.occurrenceOnOrAfter(b, LocalDate.parse("2026-08-02"), utc)
        assertEquals(first, second)
        assertEquals(
            CycleEngine.dueInstant(first!!, utc),
            CycleEngine.dueInstant(second!!, utc)
        )
    }

    @Test
    fun `due instant carries no live milliseconds`() {
        val date = LocalDate.parse("2026-06-15")
        assertEquals(0L, CycleEngine.dueInstant(date, utc) % 1000L)
    }

    @Test
    fun `cycle key survives a daylight saving transition`() {
        val newYork = ZoneId.of("America/New_York")
        val b = bill(Recurrence.MONTHLY, "2026-03-08")
        val spring = CycleEngine.occurrenceOnOrAfter(b, LocalDate.parse("2026-03-01"), newYork)!!
        assertEquals("2026-03-08", CycleEngine.cycleKey(spring))
        // The instant differs between zones, but the identity of the occurrence does not.
        assertNotEquals(
            CycleEngine.dueInstant(spring, newYork),
            CycleEngine.dueInstant(spring, utc)
        )
        assertEquals(
            CycleEngine.cycleKey(spring),
            CycleEngine.cycleKey(CycleEngine.occurrenceOnOrAfter(b, LocalDate.parse("2026-03-01"), utc)!!)
        )
    }

    @Test
    fun `changing the device timezone does not change the cycle key`() {
        val b = bill(Recurrence.MONTHLY, "2026-05-20")
        val tokyo = CycleEngine.occurrenceOnOrAfter(b, LocalDate.parse("2026-05-01"), ZoneId.of("Asia/Tokyo"))!!
        val la = CycleEngine.occurrenceOnOrAfter(b, LocalDate.parse("2026-05-01"), ZoneId.of("America/Los_Angeles"))!!
        assertEquals(CycleEngine.cycleKey(tokyo), CycleEngine.cycleKey(la))
    }

    @Test
    fun `an unpaid past cycle stays current instead of rolling forward`() {
        val b = bill(Recurrence.MONTHLY, "2026-01-10")
        val today = LocalDate.parse("2026-04-15")
        val current = CycleEngine.currentCycle(b, today, { false }, utc)
        assertEquals(LocalDate.parse("2026-01-10"), current)
    }

    @Test
    fun `a fully paid history stays on the most recent occurrence`() {
        val b = bill(Recurrence.MONTHLY, "2026-01-10")
        val today = LocalDate.parse("2026-04-15")
        val paid = setOf("2026-01-10", "2026-02-10", "2026-03-10", "2026-04-10")
        // Staying here is what keeps the paid state visible; jumping to May would show the bill as
        // unpaid again and let a second tap record a payment against a cycle nobody asked for.
        assertEquals(
            LocalDate.parse("2026-04-10"),
            CycleEngine.currentCycle(b, today, { it in paid }, utc)
        )
    }

    @Test
    fun `paying today's occurrence leaves it current so it reads as paid`() {
        val b = bill(Recurrence.MONTHLY, "2026-01-15")
        val today = LocalDate.parse("2026-08-15")
        val paid = (1..8).map { "2026-%02d-15".format(it) }.toSet()
        val current = CycleEngine.currentCycle(b, today, { it in paid }, utc)!!
        assertEquals(today, current)
        assertTrue("the current cycle must be the paid one", CycleEngine.cycleKey(current) in paid)
    }

    @Test
    fun `a bill whose first occurrence is still ahead reports that occurrence`() {
        val b = bill(Recurrence.MONTHLY, "2026-09-01")
        assertEquals(
            LocalDate.parse("2026-09-01"),
            CycleEngine.currentCycle(b, LocalDate.parse("2026-08-15"), { false }, utc)
        )
    }

    @Test
    fun `an unpaid cycle older than the lookback window is still reported`() {
        val b = bill(Recurrence.ONE_TIME, "2023-01-01")
        assertEquals(
            LocalDate.parse("2023-01-01"),
            CycleEngine.currentCycle(b, LocalDate.parse("2026-08-31"), { false }, utc)
        )
    }

    @Test
    fun `today's occurrence is current and not yet overdue`() {
        val b = bill(Recurrence.MONTHLY, "2026-04-15")
        val today = LocalDate.parse("2026-04-15")
        val current = CycleEngine.currentCycle(b, today, { false }, utc)!!
        assertEquals(today, current)
        assertTrue(!current.isBefore(today))
    }

    @Test
    fun `a paid one time bill stays visible on its own date`() {
        val b = bill(Recurrence.ONE_TIME, "2026-02-01")
        val today = LocalDate.parse("2026-04-15")
        assertEquals(
            LocalDate.parse("2026-02-01"),
            CycleEngine.currentCycle(b, today, { it == "2026-02-01" }, utc)
        )
    }

    @Test
    fun `occurrenceOnOrBefore walks back to the right occurrence`() {
        val monthly = bill(Recurrence.MONTHLY, "2026-01-31", dueDay = 31)
        assertEquals(
            LocalDate.parse("2026-02-28"),
            CycleEngine.occurrenceOnOrBefore(monthly, LocalDate.parse("2026-03-15"), utc)
        )
        val biweekly = bill(Recurrence.BIWEEKLY, "2026-01-05")
        assertEquals(
            LocalDate.parse("2026-03-16"),
            CycleEngine.occurrenceOnOrBefore(biweekly, LocalDate.parse("2026-03-20"), utc)
        )
        assertNull(
            CycleEngine.occurrenceOnOrBefore(monthly, LocalDate.parse("2025-12-31"), utc)
        )
    }

    @Test
    fun `nearestOccurrence snaps a stray date onto the grid`() {
        val quarterly = bill(Recurrence.QUARTERLY, "2024-01-10")
        // The old scheduler could record a payment in any month; snapping keeps it on the grid.
        assertEquals(
            LocalDate.parse("2025-04-10"),
            CycleEngine.nearestOccurrence(quarterly, LocalDate.parse("2025-03-10"), utc)
        )
        assertEquals(
            LocalDate.parse("2025-01-10"),
            CycleEngine.nearestOccurrence(quarterly, LocalDate.parse("2025-01-11"), utc)
        )
    }

    @Test
    fun `normalize stores an anchor and realigns the legacy day fields`() {
        val raw = Bill(
            name = "Legacy",
            amount = 1.0,
            dueDay = 31,
            recurrence = Recurrence.MONTHLY,
            createdAt = LocalDate.parse("2026-02-05").atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        )
        val normalized = CycleEngine.normalize(raw)
        assertEquals(LocalDate.parse("2026-02-28").toEpochDay(), normalized.anchorEpochDay)
        // The user asked for the 31st; February simply does not have one.
        assertEquals(31, normalized.dueDay)
        assertEquals(
            listOf("2026-02-28", "2026-03-31", "2026-04-30"),
            keys(normalized, "2026-02-01", "2026-04-30", ZoneId.systemDefault())
        )
    }

    @Test
    fun `normalize is idempotent`() {
        val once = CycleEngine.normalize(bill(Recurrence.WEEKLY, "2026-01-05", dueDay = 2))
        val twice = CycleEngine.normalize(once)
        assertEquals(once, twice)
    }

    @Test
    fun `derived weekly anchor lands on the requested calendar weekday`() {
        val raw = Bill(name = "W", amount = 1.0, dueDay = 6, recurrence = Recurrence.WEEKLY)
        val anchor = CycleEngine.deriveAnchor(raw, LocalDate.parse("2026-01-05"))
        assertEquals(LocalDate.parse("2026-01-09"), anchor)
        assertEquals(6, CycleEngine.calendarDayOfWeek(anchor))
    }

    @Test
    fun `range queries stay bounded`() {
        val b = bill(Recurrence.WEEKLY, "1990-01-01")
        val result = CycleEngine.occurrencesInRange(
            b,
            LocalDate.parse("1990-01-01"),
            LocalDate.parse("2990-01-01"),
            utc
        )
        assertEquals(CycleEngine.MAX_OCCURRENCES, result.size)
    }
}
