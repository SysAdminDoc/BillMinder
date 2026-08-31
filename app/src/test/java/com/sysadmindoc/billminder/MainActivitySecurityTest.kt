package com.sysadmindoc.billminder

import android.content.Context
import android.os.Looper
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.billminder.security.SecurityPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainActivitySecurityTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SecurityPrefs.prefs(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        SecurityPrefs.prefs(context).edit().clear().commit()
    }

    @Test
    fun `screenshot protection follows live lock configuration`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        assertSecureFlag(activity.window.attributes.flags, expected = false)

        SecurityPrefs.setPin(context, "4826")
        shadowOf(Looper.getMainLooper()).idle()
        assertSecureFlag(activity.window.attributes.flags, expected = true)

        SecurityPrefs.prefs(context).edit().remove("pin_record").commit()
        shadowOf(Looper.getMainLooper()).idle()
        assertSecureFlag(activity.window.attributes.flags, expected = false)

        controller.destroy()
    }

    private fun assertSecureFlag(flags: Int, expected: Boolean) {
        val actual = flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        assertEquals(expected, actual)
    }
}
