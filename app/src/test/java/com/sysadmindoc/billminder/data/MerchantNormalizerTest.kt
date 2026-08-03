package com.sysadmindoc.billminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantNormalizerTest {
    @Test
    fun normalizesStatementDescriptor() {
        assertEquals("Netflix", MerchantNormalizer.normalize("NETFLX.COM/BILL"))
        assertEquals("Amazon", MerchantNormalizer.normalize("amzn mktp us"))
        assertEquals("Lowe's", MerchantNormalizer.normalize("LOWES"))
    }

    @Test
    fun leavesUnknownNamesReadable() {
        assertEquals("Local Water Co", MerchantNormalizer.normalize("  Local   Water   Co  "))
    }

    @Test
    fun shipsAtLeastThreeHundredAliases() {
        assertTrue(MerchantNormalizer.aliasCount >= 300)
    }
}
