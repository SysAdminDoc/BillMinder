package com.sysadmindoc.billminder.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptOcrTest {
    @Test
    fun parsesLabeledTotalAndIsoDate() {
        val result = ReceiptOcrParser.parse(
            "Receipt\nTotal: \\$1,249.50\nPaid on 2026-07-31",
            LocalDate.of(2026, 8, 3)
        )

        assertEquals(1249.50, result.amount!!, 0.001)
        assertEquals(LocalDate.of(2026, 7, 31), result.date)
    }

    @Test
    fun parsesMonthDateWithoutYearAndLabeledTotal() {
        val result = ReceiptOcrParser.parse(
            "Paid\nJul 28\nSubtotal 10.00\nTax 1.25\nTotal 11.25",
            LocalDate.of(2026, 8, 3)
        )

        assertEquals(11.25, result.amount!!, 0.001)
        assertEquals(LocalDate.of(2026, 7, 28), result.date)
    }

    @Test
    fun ignoresUnsupportedDateAndAmountWithoutReceiptSignals() {
        val result = ReceiptOcrParser.parse(
            "Order number 12345\nReference 2026/08/03\nThank you",
            LocalDate.of(2026, 8, 3)
        )

        assertNull(result.amount)
        assertEquals(LocalDate.of(2026, 8, 3), result.date)
    }
}
