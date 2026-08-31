package com.sysadmindoc.billminder

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.annotation.XmlRes
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class BackupExclusionRulesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val excludedDomains = setOf("root", "file", "database", "sharedpref", "external")

    @Test
    fun `platform backup is disabled`() {
        assertFalse(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    @Test
    fun `legacy backup rules exclude every persistent data domain`() {
        assertEquals(excludedDomains.associateWith { 1 }, exclusions(R.xml.backup_rules))
    }

    @Test
    fun `cloud and device transfer rules both exclude every persistent data domain`() {
        assertEquals(excludedDomains.associateWith { 2 }, exclusions(R.xml.data_extraction_rules))
    }

    private fun exclusions(@XmlRes resource: Int): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        context.resources.getXml(resource).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                    val domain = parser.getAttributeValue(null, "domain")
                    val path = parser.getAttributeValue(null, "path")
                    assertEquals(".", path)
                    counts[domain] = counts.getOrDefault(domain, 0) + 1
                }
                parser.next()
            }
        }
        return counts
    }
}
