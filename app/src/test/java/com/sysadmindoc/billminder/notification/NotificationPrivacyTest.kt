package com.sysadmindoc.billminder.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.billminder.security.SecurityPrefs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotificationPrivacyTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecurityPrefs.prefs(context).edit().clear().commit()
        NotificationHelper.createChannels(context)
        SecurityPrefs.setMaskExternalContent(context, true)
    }

    @After
    fun tearDown() {
        SecurityPrefs.prefs(context).edit().clear().commit()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    }

    @Test
    fun `reminder notification masks the bill name and amount`() {
        NotificationHelper.showReminderNotification(
            context = context,
            billId = 42L,
            billName = "Electric Company",
            amount = 123.45,
            daysUntilDue = 1,
            isAutoPay = false,
            cycleKey = "2026-09-01",
            currency = "USD"
        )

        val notification = shadowOf(
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ).allNotifications.single()
        val visibleText = listOf(
            notification.extras.getCharSequence(Notification.EXTRA_TITLE),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT),
            notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        ).joinToString(" ")

        assertTrue(visibleText.contains("Bill due"))
        assertTrue(visibleText.contains("Amount hidden"))
        assertFalse(visibleText.contains("Electric Company"))
        assertFalse(visibleText.contains("123.45"))
    }

    private fun showReminder(billId: Long) {
        NotificationHelper.showReminderNotification(
            context = context,
            billId = billId,
            billName = "Water",
            amount = 20.0,
            daysUntilDue = 0,
            isAutoPay = false,
            cycleKey = "2026-09-01",
            currency = "USD"
        )
    }

    private fun lastNotification(): Notification = shadowOf(
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    ).allNotifications.single()

    @Test
    fun `the full-screen intent stays off while the preference is off`() {
        ReminderPrefs.setFullScreenEnabled(context, false)

        showReminder(7L)

        assertNull(lastNotification().fullScreenIntent)
    }

    /**
     * Below Android 14 the grant is automatic, so the preference alone decides. This is the half of
     * the gate a unit test can reach: Robolectric 4.16 does not shadow `canUseFullScreenIntent`, so
     * the denied-grant branch on Android 14 and newer cannot be driven from here.
     */
    @Test
    @Config(sdk = [33])
    fun `the full-screen intent is attached when the preference is on and the platform allows it`() {
        ReminderPrefs.setFullScreenEnabled(context, true)

        showReminder(8L)

        assertNotNull(lastNotification().fullScreenIntent)
    }
}
