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

    @Test
    fun `a shared message names the merchant it mentions`() {
        val candidate = SmsBillParser.parse(
            sender = null,
            body = "Your Verizon bill of \$84.30 is due on 09/15. Pay now to avoid a late fee.",
            today = today
        )
        assertEquals("Verizon", candidate?.name)
        assertEquals(84.30, candidate?.amount ?: 0.0, 0.001)
        assertEquals(LocalDate.of(2026, 9, 15), candidate?.dueDate)
    }

    @Test
    fun `a shared message with no merchant still parses`() {
        val candidate = SmsBillParser.parse(
            sender = null,
            body = "Payment of \$20.00 due 09/01.",
            today = today
        )
        assertEquals("Shared bill", candidate?.name)
        assertEquals(20.0, candidate?.amount ?: 0.0, 0.001)
    }

    @Test
    fun `a numeric shortcode falls back to the merchant in the body`() {
        val candidate = SmsBillParser.parse(
            sender = "12345",
            body = "Your Duke Energy bill is due 09/10 for \$61.00.",
            today = today
        )
        assertEquals("Duke Energy", candidate?.name)
    }
}
