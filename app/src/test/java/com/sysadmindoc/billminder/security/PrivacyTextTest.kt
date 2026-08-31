package com.sysadmindoc.billminder.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyTextTest {
    @Test
    fun `privacy labels reveal no name or amount`() {
        assertEquals(PrivacyText.HIDDEN_BILL_NAME, PrivacyText.externalBillName("Electric", true))
        assertEquals(PrivacyText.HIDDEN_EXTERNAL_AMOUNT, PrivacyText.externalAmount("$123.45", true))
        assertEquals(PrivacyText.HIDDEN_AMOUNT, PrivacyText.inAppAmount("$123.45", true))
    }

    @Test
    fun `privacy labels preserve values when disabled`() {
        assertEquals("Electric", PrivacyText.externalBillName("Electric", false))
        assertEquals("$123.45", PrivacyText.externalAmount("$123.45", false))
        assertEquals("$123.45", PrivacyText.inAppAmount("$123.45", false))
    }
}
