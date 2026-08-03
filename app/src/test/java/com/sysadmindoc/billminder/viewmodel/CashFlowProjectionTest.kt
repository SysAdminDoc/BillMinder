package com.sysadmindoc.billminder.viewmodel

import com.sysadmindoc.billminder.data.Payment
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashFlowProjectionTest {
    @Test
    fun alwaysReturnsTheNextTwelveCalendarMonths() {
        val now = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.AUGUST, 3, 12, 0, 0)
        }.timeInMillis

        val result = CashFlowProjection.build(emptyList(), emptyList(), now)

        assertEquals(12, result.size)
        assertEquals("Aug 26", result.first().label)
        assertEquals("Jul 27", result.last().label)
    }

    @Test
    fun paymentsAreConvertedAndGroupedByPaidMonth() {
        val now = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.AUGUST, 3, 12, 0, 0)
        }.timeInMillis
        val paidAt = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.SEPTEMBER, 8, 12, 0, 0)
        }.timeInMillis
        val payment = Payment(
            billId = 4,
            amount = 10.0,
            paidAt = paidAt,
            dueDate = paidAt,
            currency = "USD"
        )

        val result = CashFlowProjection.build(emptyList(), listOf(payment), now)

        assertEquals(0.0, result[0].paid, 0.0001)
        assertEquals(10.0, result[1].paid, 0.0001)
        assertTrue(result[1].outstanding == 0.0)
    }
}
