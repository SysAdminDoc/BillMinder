package com.sysadmindoc.billminder.data

import java.util.Calendar
import org.junit.Assert.assertTrue
import org.junit.Test

class InterchangeExportTest {
    private val bill = Bill(
        id = 7,
        name = "Power, Inc.",
        amount = 120.0,
        dueDay = 15,
        category = BillCategory.UTILITIES,
        currency = "EUR",
        tags = "home"
    )
    private val payment = Payment(
        billId = 7,
        amount = 100.0,
        paidAt = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.AUGUST, 15, 12, 0, 0)
        }.timeInMillis,
        dueDate = 0L,
        note = "Autopay",
        confirmationNumber = "ABC-123",
        currency = "EUR"
    )

    @Test
    fun bluecoinsExportUsesAdvancedExpenseTemplateAndEscapesCells() {
        val csv = InterchangeExporter.export(InterchangeFormat.BLUECOINS, listOf(bill), listOf(payment))

        assertTrue(csv.startsWith("Transaction type,Date,Item Name"))
        assertTrue(csv.contains("e,"))
        assertTrue(csv.contains("\"Power, Inc.\""))
        assertTrue(csv.contains(",EUR,1.0"))
    }

    @Test
    fun ynabExportUsesPositiveOutflowAndEmptyInflow() {
        val csv = InterchangeExporter.export(InterchangeFormat.YNAB, listOf(bill), listOf(payment), "USD")

        assertTrue(csv.startsWith("Date,Payee,Category,Memo,Outflow,Inflow"))
        assertTrue(csv.contains(",108.70,"))
        assertTrue(csv.contains("Native currency: EUR"))
        assertTrue(csv.trimEnd().endsWith(","))
    }

    @Test
    fun actualExportUsesNegativeOutflowAmount() {
        val csv = InterchangeExporter.export(InterchangeFormat.ACTUAL, listOf(bill), listOf(payment), "USD")

        assertTrue(csv.startsWith("Date,Payee,Notes,Category,Amount"))
        assertTrue(csv.contains(",-108.70"))
    }
}
