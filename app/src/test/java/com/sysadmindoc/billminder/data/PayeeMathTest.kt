package com.sysadmindoc.billminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayeeMathTest {
    @Test
    fun calculatesRoommateShare() {
        assertEquals(37.50, PayeeMath.shareAmount(150.0, 25.0), 0.001)
    }

    @Test
    fun balancedSharesMustTotalOneHundredPercent() {
        assertTrue(PayeeMath.isBalanced(listOf(PayeeDraft("Me", 50.0), PayeeDraft("Roommate", 50.0))))
        assertFalse(PayeeMath.isBalanced(listOf(PayeeDraft("Me", 60.0))))
    }
}
