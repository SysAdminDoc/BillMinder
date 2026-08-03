package com.sysadmindoc.billminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillValidationTest {
    @Test
    fun acceptsExpectedAmountInsideRange() {
        assertNull(BillValidation.variableAmountError(75.0, 50.0, 100.0))
    }

    @Test
    fun rejectsReversedRange() {
        assertEquals(
            "Min amount cannot be higher than max",
            BillValidation.variableAmountError(75.0, 100.0, 50.0)
        )
    }

    @Test
    fun rejectsExpectedAmountOutsideRange() {
        assertEquals(
            "Expected amount must be within the range",
            BillValidation.variableAmountError(125.0, 50.0, 100.0)
        )
    }
}
