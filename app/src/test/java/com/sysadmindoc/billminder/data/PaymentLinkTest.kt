package com.sysadmindoc.billminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaymentLinkTest {

    @Test
    fun webAddressesAreOpened() {
        assertEquals("example.com", PaymentLink.host("https://example.com/pay"))
        assertEquals("example.com", PaymentLink.host("http://example.com/pay"))
        assertEquals("example.com", PaymentLink.host("  https://example.com/pay  "))
    }

    @Test
    fun aTypedHostGetsHttps() {
        val link = PaymentLink.parse("example.com/pay")
        assertEquals("https", link?.scheme)
        assertEquals("example.com", link?.host)
    }

    @Test
    fun schemesOutsideTheWebAreRefused() {
        // Each of these would otherwise be handed to ACTION_VIEW, letting whatever app claims the
        // scheme act on a value that reached the bill through an import or a restored backup.
        listOf(
            "javascript:alert(1)",
            "intent://scan/#Intent;scheme=zxing;package=com.example;end",
            "file:///data/data/com.sysadmindoc.billminder/databases/bills.db",
            "content://com.example.provider/secret",
            "market://details?id=com.example",
            "tel:+15551234567",
            "sms:+15551234567",
            "JavaScript:alert(1)"
        ).forEach { hostile ->
            assertNull("$hostile was accepted as a payment link", PaymentLink.parse(hostile))
            assertFalse("$hostile passed the save guard", PaymentLink.isAcceptable(hostile))
        }
    }

    @Test
    fun addressesWithNoHostAreRefused() {
        assertNull(PaymentLink.parse("https://"))
        assertNull(PaymentLink.parse("http:///pay"))
    }

    @Test
    fun blankIsAcceptableBecauseTheFieldIsOptional() {
        assertTrue(PaymentLink.isAcceptable(""))
        assertTrue(PaymentLink.isAcceptable("   "))
        assertNull(PaymentLink.parse(""))
        assertNull(PaymentLink.host(""))
    }
}
