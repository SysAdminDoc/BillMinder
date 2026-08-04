package com.sysadmindoc.billminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CsvMappingLearningTest {
    private val headers = listOf("Description", "Amount", "Due", "Merchant")
    private val baseline = mapOf(
        CsvField.NAME to 0,
        CsvField.AMOUNT to 1,
        CsvField.DUE_DATE to 2
    )
    private val corrected = baseline + (CsvField.NAME to 3)

    @Test
    fun learnedColumnRequiresThreeCorrections() {
        var learned = emptyMap<String, CsvLearnedCorrection>()
        repeat(2) {
            learned = CsvMappingLearner.recordCorrections(headers, baseline, corrected, learned)
        }

        assertNotEquals(3, learned["merchant"]?.confirmations)
        assertEquals(0, CsvMappingLearner.applyLearned(headers, baseline, learned)[CsvField.NAME])

        learned = CsvMappingLearner.recordCorrections(headers, baseline, corrected, learned)

        assertEquals(3, learned["merchant"]?.confirmations)
        assertEquals(3, CsvMappingLearner.applyLearned(headers, baseline, learned)[CsvField.NAME])
    }

    @Test
    fun aDifferentCorrectionStartsOverForThatHeader() {
        var learned = emptyMap<String, CsvLearnedCorrection>()
        repeat(2) {
            learned = CsvMappingLearner.recordCorrections(headers, baseline, corrected, learned)
        }
        val changed = baseline + (CsvField.NAME to 1)
        learned = CsvMappingLearner.recordCorrections(headers, baseline, changed, learned)

        assertEquals(1, learned["amount"]?.confirmations)
        assertEquals(2, learned["merchant"]?.confirmations)
    }
}
