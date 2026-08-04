package com.sysadmindoc.billminder.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsImportTest {
    private val today = LocalDate.of(2026, 8, 3)

    @Test
    fun parsesCurrencyAndMonthDate() {
        val candidate = SmsBillParser.parse(
            sender = "Verizon",
            body = "Your Verizon bill of $" + "84.50 is due on September 15, 2026.",
            today = today
        )

        requireNotNull(candidate)
        assertEquals("Verizon", candidate.name)
        assertEquals(84.50, candidate.amount, 0.001)
        assertEquals("USD", candidate.currency)
        assertEquals(LocalDate.of(2026, 9, 15), candidate.dueDate)
    }

    @Test
    fun rollsYearForwardWhenDateWithoutYearAlreadyPassed() {
        val candidate = SmsBillParser.parse(
            sender = "55512",
            body = "Payment of EUR 42.00 due 01/12.",
            today = today
        )

        requireNotNull(candidate)
        assertEquals("SMS bill", candidate.name)
        assertEquals(LocalDate.of(2027, 1, 12), candidate.dueDate)
        assertEquals("EUR", candidate.currency)
    }

    @Test
    fun ignoresMessagesWithoutDueSignalOrAmount() {
        assertNull(SmsBillParser.parse("Bank", "Your balance is $" + "20.00.", today))
        assertNull(SmsBillParser.parse("Bank", "Your payment is due tomorrow.", today))
    }
}
