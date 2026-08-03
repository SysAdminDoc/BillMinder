package com.sysadmindoc.billminder.data

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportTest {
    @Test
    fun parserPreservesQuotedCommasNewlinesAndEscapedQuotes() {
        val table = CsvImport.parse(
            "Name,Amount,Notes\n" +
                "\"ACME, Inc.\",10,\"Line one\nLine two\"\n" +
                "Other,12,\"He said \"\"hello\"\"\"\n"
        )

        assertEquals(listOf("Name", "Amount", "Notes"), table.headers)
        assertEquals("ACME, Inc.", table.rows[0][0])
        assertEquals("Line one\nLine two", table.rows[0][2])
        assertEquals("He said \"hello\"", table.rows[1][2])
    }

    @Test
    fun suggestionsRecognizeBillminderExportColumns() {
        val headers = listOf(
            "Bill Name", "Category", "Bill Currency", "Amount", "Due Day",
            "Payment Date", "Payment Currency", "Payment Amount"
        )
        val mapping = CsvImport.suggestedMapping(headers)

        assertEquals(0, mapping[CsvField.NAME])
        assertEquals(3, mapping[CsvField.AMOUNT])
        assertEquals(4, mapping[CsvField.DUE_DAY])
        assertEquals(5, mapping[CsvField.PAYMENT_DATE])
        assertEquals(7, mapping[CsvField.PAYMENT_AMOUNT])
    }

    @Test
    fun valuesParseMoneyAndStrictDates() {
        assertEquals(1234.5, requireNotNull(CsvValueParser.amount("\$1,234.50")), 0.0001)
        assertTrue(CsvValueParser.dateMillis("2026-08-03") != null)
        assertEquals(null, CsvValueParser.dateMillis("2026-99-99"))
        val date = CsvValueParser.dateMillis("2026-08-03") ?: error("date did not parse")
        val calendar = Calendar.getInstance().apply { timeInMillis = date }
        assertEquals(Calendar.AUGUST, calendar.get(Calendar.MONTH))
        assertEquals(3, calendar.get(Calendar.DAY_OF_MONTH))
    }
}
