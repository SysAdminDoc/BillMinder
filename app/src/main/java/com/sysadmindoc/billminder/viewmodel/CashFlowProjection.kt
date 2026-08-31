package com.sysadmindoc.billminder.viewmodel

import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.CurrencyConverter
import com.sysadmindoc.billminder.data.Payment
import com.sysadmindoc.billminder.domain.BillCycles
import com.sysadmindoc.billminder.domain.CycleEngine
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar

data class MonthlyCashFlow(
    val label: String,
    val paid: Double,
    val outstanding: Double
)

object CashFlowProjection {
    private val monthNames = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun build(
        bills: List<Bill>,
        payments: List<Payment>,
        nowMillis: Long = System.currentTimeMillis(),
        toDisplay: (Double, String) -> Double = { amount, currency ->
            CurrencyConverter.convert(amount, currency, "USD")
        }
    ): List<MonthlyCashFlow> {
        val firstMonth = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthStarts = (0 until 12).map { offset ->
            (firstMonth.clone() as Calendar).apply { add(Calendar.MONTH, offset) }
        }
        val paid = DoubleArray(12)
        val outstanding = DoubleArray(12)
        val billCurrencies = bills.associate { it.id to it.currency }

        payments.forEach { payment ->
            val monthIndex = monthIndex(monthStarts, payment.paidAt)
            if (monthIndex != null) {
                val currency = payment.currency.ifBlank { billCurrencies[payment.billId] ?: "USD" }
                paid[monthIndex] += toDisplay(payment.amount, currency)
            }
        }

        val zone = ZoneId.systemDefault()
        val windowStart = CycleEngine.toLocalDate(monthStarts.first().timeInMillis, zone)
        val windowEnd = windowStart.plusMonths(12).minusDays(1)
        BillCycles.rangeSnapshot(
            bills = bills.filter(Bill::isEnabled),
            payments = payments,
            start = windowStart,
            endInclusive = windowEnd,
            today = windowStart,
            zone = zone,
            convert = toDisplay
        ).occurrences.filterNot { it.cycle.isPaid }.forEach { occurrence ->
            val date = occurrence.cycle.date
            val monthIndex = ChronoUnit.MONTHS.between(
                YearMonth.from(windowStart),
                YearMonth.from(date)
            ).toInt()
            if (monthIndex in outstanding.indices) {
                outstanding[monthIndex] += toDisplay(
                    occurrence.bill.amount,
                    occurrence.bill.currency
                )
            }
        }

        return monthStarts.indices.map { index ->
            val month = monthStarts[index]
            MonthlyCashFlow(
                label = "${monthNames[month.get(Calendar.MONTH)]} ${month.get(Calendar.YEAR) % 100}",
                paid = paid[index],
                outstanding = outstanding[index]
            )
        }
    }

    private fun monthIndex(monthStarts: List<Calendar>, millis: Long): Int? {
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        val first = monthStarts.first()
        val index = (target.get(Calendar.YEAR) - first.get(Calendar.YEAR)) * 12 +
            target.get(Calendar.MONTH) - first.get(Calendar.MONTH)
        return index.takeIf { it in monthStarts.indices }
    }
}
