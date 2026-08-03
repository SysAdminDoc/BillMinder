package com.sysadmindoc.billminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetMathTest {
    @Test
    fun progressClampsTheRingButKeepsRemainingAtZeroWhenOverLimit() {
        val progress = BudgetMath.progress(spent = 125.0, limit = 100.0)

        requireNotNull(progress)
        assertEquals(1.0f, progress.ratio)
        assertEquals(0.0, progress.remaining, 0.0001)
        assertEquals(125.0, progress.spent, 0.0001)
    }

    @Test
    fun invalidLimitDoesNotCreateAProgressRing() {
        assertNull(BudgetMath.progress(spent = 10.0, limit = 0.0))
        assertNull(BudgetMath.progress(spent = 10.0, limit = Double.NaN))
    }
}
