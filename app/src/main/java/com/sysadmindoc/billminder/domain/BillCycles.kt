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

object BillCycles {

    fun paidKeys(payments: List<Payment>): Map<Long, Set<String>> =
        payments.groupBy { it.billId }
            .mapValues { (_, list) -> list.mapTo(mutableSetOf()) { it.cycleKey } }

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

    /**
     * Unpaid occurrences for [bill] between [start] and [endInclusive], newest last. Used by the
     * forecast, cash-flow, and month-total surfaces so they share one definition of "upcoming".
     */
    fun unpaidOccurrences(
        bill: Bill,
        paidKeys: Set<String>,
        start: LocalDate,
        endInclusive: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<LocalDate> =
        CycleEngine.occurrencesInRange(bill, start, endInclusive, zone)
            .filter { CycleEngine.cycleKey(it) !in paidKeys }
}
