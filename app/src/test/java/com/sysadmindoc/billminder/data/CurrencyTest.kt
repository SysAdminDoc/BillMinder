package com.sysadmindoc.billminder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyTest {
    @Test
    fun convertsUsingBundledUsdSnapshot() {
        assertEquals(9.2, CurrencyConverter.convert(10.0, "USD", "EUR"), 0.0001)
        assertEquals(10.0, CurrencyConverter.convert(9.2, "EUR", "USD"), 0.0001)
    }

    @Test
    fun manualRateOverridesBundledRate() {
        assertEquals(
            10.0,
            CurrencyConverter.convert(10.0, "USD", "EUR", mapOf("EUR" to 1.0)),
            0.0001
        )
    }

    @Test
    fun formatterUsesCurrencyCodeRules() {
        assertEquals("€1,234.50", CurrencyFormatter.format(1234.5, "EUR"))
        assertEquals("¥1,235", CurrencyFormatter.format(1234.5, "JPY"))
    }
}
