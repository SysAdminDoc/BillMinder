package com.sysadmindoc.billminder.domain

import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.Payment
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The occurrence a bill is currently sitting on, together with everything the UI, notifications,
 * widgets, and the watch need to describe it. Every surface resolves state through here so they
 * cannot disagree about what is due or paid.
 */
data class ResolvedCycle(
    val billId: Long,
    val date: LocalDate,
    val cycleKey: String,
    val dueAt: Long,
    val daysUntilDue: Int,
    val isPaid: Boolean,
    val isOverdue: Boolean,
    val payment: Payment?
)

data class ResolvedBillCycle(
    val bill: Bill,
    val cycle: ResolvedCycle
)

data class CycleRangeSnapshot(
    val occurrences: List<ResolvedBillCycle>,
    val totalDue: Double,
    val totalPaid: Double,
    val remaining: Double,
    val billCount: Int,
    val occurrenceCount: Int,
    val paidCount: Int,
    val overdueCount: Int,
    val nextDue: ResolvedBillCycle?
)

object BillCycles {

    fun resolve(
        bill: Bill,
        payments: List<Payment>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): ResolvedCycle? {
        val forBill = payments.filter { it.billId == bill.id }
        return resolve(bill, forBill.mapTo(mutableSetOf()) { it.cycleKey }, forBill, today, zone)
    }

    fun resolve(
        bill: Bill,
        paidKeys: Set<String>,
        payments: List<Payment>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): ResolvedCycle? {
        val date = CycleEngine.currentCycle(bill, today, { it in paidKeys }, zone) ?: return null
        val key = CycleEngine.cycleKey(date)
        val isPaid = key in paidKeys
        return ResolvedCycle(
            billId = bill.id,
            date = date,
            cycleKey = key,
            dueAt = CycleEngine.dueInstant(date, zone),
            daysUntilDue = ChronoUnit.DAYS.between(today, date).toInt(),
            isPaid = isPaid,
            isOverdue = !isPaid && date.isBefore(today),
            payment = payments.firstOrNull { it.billId == bill.id && it.cycleKey == key }
        )
    }

    fun currentCycles(
        bills: List<Bill>,
        payments: List<Payment>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<ResolvedBillCycle> {
        val paymentsByBill = payments.groupBy(Payment::billId)
        return bills.mapNotNull { bill ->
            val forBill = paymentsByBill[bill.id].orEmpty()
            resolve(
                bill = bill,
                paidKeys = forBill.mapTo(mutableSetOf(), Payment::cycleKey),
                payments = forBill,
                today = today,
                zone = zone
            )?.let { ResolvedBillCycle(bill, it) }
        }.sortedWith(compareBy({ it.cycle.dueAt }, { it.bill.id }))
    }

    fun rangeSnapshot(
        bills: List<Bill>,
        payments: List<Payment>,
        start: LocalDate,
        endInclusive: LocalDate,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        convert: (Double, String) -> Double = { amount, _ -> amount }
    ): CycleRangeSnapshot {
        val paymentsByCycle = payments.associateBy { it.billId to it.cycleKey }
        val occurrences = bills.flatMap { bill ->
            CycleEngine.occurrencesInRange(bill, start, endInclusive, zone).map { date ->
                val key = CycleEngine.cycleKey(date)
                val payment = paymentsByCycle[bill.id to key]
                val isPaid = payment != null
                ResolvedBillCycle(
                    bill = bill,
                    cycle = ResolvedCycle(
                        billId = bill.id,
                        date = date,
                        cycleKey = key,
                        dueAt = CycleEngine.dueInstant(date, zone),
                        daysUntilDue = ChronoUnit.DAYS.between(today, date).toInt(),
                        isPaid = isPaid,
                        isOverdue = !isPaid && date.isBefore(today),
                        payment = payment
                    )
                )
            }
        }.sortedWith(compareBy({ it.cycle.date }, { it.bill.id }))

        val totalDue = occurrences.sumOf { convert(it.bill.amount, it.bill.currency) }
        val totalPaid = occurrences.sumOf { occurrence ->
            occurrence.cycle.payment?.let { payment ->
                convert(
                    payment.amount,
                    payment.currency.ifBlank { occurrence.bill.currency }
                )
            } ?: 0.0
        }
        val remaining = occurrences.filterNot { it.cycle.isPaid }
            .sumOf { convert(it.bill.amount, it.bill.currency) }
        val paidCount = occurrences.count { it.cycle.isPaid }
        return CycleRangeSnapshot(
            occurrences = occurrences,
            totalDue = totalDue,
            totalPaid = totalPaid,
            remaining = remaining,
            billCount = bills.map(Bill::id).distinct().size,
            occurrenceCount = occurrences.size,
            paidCount = paidCount,
            overdueCount = occurrences.count { it.cycle.isOverdue },
            nextDue = occurrences
                .filter { !it.cycle.isPaid && !it.cycle.isOverdue }
                .minWithOrNull(compareBy({ it.cycle.date }, { it.bill.id }))
        )
    }

}
