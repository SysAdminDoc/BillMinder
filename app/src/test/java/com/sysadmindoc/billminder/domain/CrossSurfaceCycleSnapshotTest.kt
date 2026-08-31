package com.sysadmindoc.billminder.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.BillRepository
import com.sysadmindoc.billminder.data.Payment
import com.sysadmindoc.billminder.data.Recurrence
import com.sysadmindoc.billminder.ui.screens.buildCalendarOccurrences
import com.sysadmindoc.billminder.viewmodel.CashFlowProjection
import com.sysadmindoc.billminder.viewmodel.billStatusesFrom
import com.sysadmindoc.billminder.viewmodel.forecastFrom
import com.sysadmindoc.billminder.viewmodel.monthlySummaryFrom
import com.sysadmindoc.billminder.widget.lockScreenSnapshotFrom
import com.sysadmindoc.billminder.widget.monthTotalSnapshotFrom
import com.sysadmindoc.billminder.widget.upcomingWidgetSnapshotFrom
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class CrossSurfaceCycleSnapshotTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("UTC")

    @Test
    fun `one database range produces the same calendar summary forecast and widget snapshot`() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, BillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = BillRepository(database)
            val weeklyId = repository.insertBill(
                bill("Weekly", 10.0, Recurrence.WEEKLY, "2026-02-02")
            )
            val monthlyId = repository.insertBill(
                bill("Month end", 30.0, Recurrence.MONTHLY, "2026-01-31")
            )
            val quarterlyId = repository.insertBill(
                bill("Quarter end", 90.0, Recurrence.QUARTERLY, "2025-11-30")
            )
            pay(repository, weeklyId, "2026-02-02", 11.0)
            pay(repository, weeklyId, "2026-02-09", 10.0)

            val today = LocalDate.parse("2026-02-15")
            val bills = repository.getAllBillsList()
            val payments = repository.getAllPaymentsForExport()
            val snapshot = BillCycles.rangeSnapshot(
                bills = bills,
                payments = payments,
                start = LocalDate.parse("2026-02-01"),
                endInclusive = LocalDate.parse("2026-02-28"),
                today = today,
                zone = zone
            )
            val summary = monthlySummaryFrom(snapshot, "USD")
            val calendar = buildCalendarOccurrences(snapshot)
            val widget = monthTotalSnapshotFrom(snapshot)
            val forecast = forecastFrom(snapshot)
            val current = BillCycles.currentCycles(bills, payments, today, zone)
            val home = billStatusesFrom(current)
            val upcomingWidget = upcomingWidgetSnapshotFrom(current, "USD")
            val lockWidget = lockScreenSnapshotFrom(current)
            val cashFlow = CashFlowProjection.build(
                bills = bills,
                payments = payments,
                nowMillis = today.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
            )

            val canonicalCycles = snapshot.occurrences.map { it.bill.id to it.cycle.cycleKey }
            val calendarCycles = calendar.toSortedMap().values.flatten()
                .map { it.bill.id to it.cycleKey }
            assertEquals(canonicalCycles, calendarCycles)
            assertEquals(canonicalCycles, widget.cycles)
            assertEquals(
                snapshot.occurrences.map {
                    listOf(it.bill.id, it.cycle.cycleKey, it.cycle.isPaid, it.cycle.isOverdue)
                },
                calendar.toSortedMap().values.flatten().map {
                    listOf(it.bill.id, it.cycleKey, it.isPaid, it.isOverdue)
                }
            )

            assertEquals(6, snapshot.occurrenceCount)
            assertEquals(3, snapshot.billCount)
            assertEquals(2, snapshot.paidCount)
            assertEquals(0, snapshot.overdueCount)
            assertEquals(160.0, snapshot.totalDue, 0.0001)
            assertEquals(21.0, snapshot.totalPaid, 0.0001)
            assertEquals(140.0, snapshot.remaining, 0.0001)

            assertEquals(snapshot.totalDue, summary.totalDue, 0.0001)
            assertEquals(snapshot.totalPaid, summary.totalPaid, 0.0001)
            assertEquals(snapshot.remaining, summary.remaining, 0.0001)
            assertEquals(snapshot.occurrenceCount, summary.occurrenceCount)
            assertEquals("2026-02-16", summary.nextDueBill?.cycleKey)

            assertEquals(snapshot.totalDue, widget.totalDue, 0.0001)
            assertEquals(snapshot.totalPaid, widget.totalPaid, 0.0001)
            assertEquals(snapshot.remaining, widget.remaining, 0.0001)
            assertEquals(snapshot.paidCount, widget.paidCount)
            assertEquals(snapshot.occurrenceCount, widget.totalCount)

            assertEquals(140.0, forecast.next30Days, 0.0001)
            assertEquals(4, forecast.next30Bills)
            assertEquals(21.0, cashFlow.first().paid, 0.0001)
            assertEquals(140.0, cashFlow.first().outstanding, 0.0001)

            assertEquals(
                current.map { Triple(it.bill.id, it.cycle.cycleKey, it.cycle.isPaid) },
                home.map { Triple(it.bill.id, it.cycleKey, it.isPaidThisCycle) }
            )
            assertEquals(
                current.filterNot { it.cycle.isPaid }.map {
                    listOf(it.bill.id, it.cycle.cycleKey, it.cycle.daysUntilDue, it.cycle.isOverdue)
                },
                upcomingWidget.items.map {
                    listOf(it.billId, it.cycleKey, it.daysUntilDue, it.isOverdue)
                }
            )
            assertEquals(upcomingWidget.items.first().billId, lockWidget?.billId)
            assertEquals(upcomingWidget.items.first().cycleKey, lockWidget?.cycleKey)
            assertEquals(upcomingWidget.items.first().daysUntilDue, lockWidget?.daysUntilDue)
            assertEquals(listOf(2, 9, 16, 23), calendar.keys.filter { day ->
                calendar.getValue(day).any { it.bill.id == weeklyId }
            })
            assertEquals(
                setOf(monthlyId, quarterlyId),
                calendar.getValue(28).map { it.bill.id }.toSet()
            )
        } finally {
            database.close()
        }
    }

    private fun bill(
        name: String,
        amount: Double,
        recurrence: Recurrence,
        anchor: String
    ): Bill {
        val date = LocalDate.parse(anchor)
        return Bill(
            name = name,
            amount = amount,
            dueDay = if (recurrence == Recurrence.WEEKLY) {
                CycleEngine.calendarDayOfWeek(date)
            } else {
                date.dayOfMonth
            },
            recurrence = recurrence,
            anchorEpochDay = date.toEpochDay()
        )
    }

    private suspend fun pay(
        repository: BillRepository,
        billId: Long,
        cycleKey: String,
        amount: Double
    ) {
        val date = LocalDate.parse(cycleKey)
        repository.insertPayment(
            Payment(
                billId = billId,
                amount = amount,
                paidAt = CycleEngine.dueInstant(date, zone),
                dueDate = CycleEngine.dueInstant(date, zone),
                cycleKey = cycleKey
            )
        )
    }
}
