package com.sysadmindoc.billminder.security

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class SecurityPrefsTest {
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
    fun `new PIN uses a versioned slow KDF record`() {
        assertTrue(SecurityPrefs.setPin(context, "4286"))

        val prefs = SecurityPrefs.prefs(context)
        val record = prefs.getString("pin_record", null)
        assertNotNull(record)
        assertTrue(record!!.startsWith("v2|pbkdf2-sha256|600000|"))
        assertFalse(record.contains("4286"))
        assertNull(prefs.getString("pin_hash", null))
        assertNull(prefs.getString("pin_salt", null))
        assertTrue(SecurityPrefs.verifyPin(context, "4286"))
        assertFalse(SecurityPrefs.verifyPin(context, "4287"))
    }

    @Test
    fun `successful unlock migrates the existing salted SHA record`() {
        val salt = ByteArray(16) { it.toByte() }
        val saltText = Base64.encodeToString(salt, Base64.NO_WRAP)
        val legacyHash = MessageDigest.getInstance("SHA-256")
            .digest(salt + "2468".toByteArray())
        SecurityPrefs.prefs(context).edit()
            .putString("pin_salt", saltText)
            .putString("pin_hash", Base64.encodeToString(legacyHash, Base64.NO_WRAP))
            .commit()

        assertTrue(SecurityPrefs.verifyPin(context, "2468"))
        assertTrue(SecurityPrefs.prefs(context).getString("pin_record", null)!!.startsWith("v2|"))
        assertNull(SecurityPrefs.prefs(context).getString("pin_hash", null))
        assertNull(SecurityPrefs.prefs(context).getString("pin_salt", null))
    }

    @Test
    fun `successful unlock migrates the oldest plaintext PIN`() {
        SecurityPrefs.prefs(context).edit().putString("pin_code", "9753").commit()

        assertTrue(SecurityPrefs.verifyPin(context, "9753"))
        assertTrue(SecurityPrefs.prefs(context).getString("pin_record", null)!!.startsWith("v2|"))
        assertNull(SecurityPrefs.prefs(context).getString("pin_code", null))
    }

    @Test
    fun `duress PIN uses the versioned KDF and returns the decoy result`() {
        assertTrue(SecurityPrefs.setPin(context, "2468"))
        assertTrue(SecurityPrefs.setDuressPin(context, "1357"))
        assertTrue(SecurityPrefs.prefs(context).getString("duress_pin_record", null)!!.startsWith("v2|"))

        assertEquals(PinUnlockResult.Duress, SecurityPrefs.attemptUnlock(context, "1357", 10_000L))
    }

    @Test
    fun `biometric lock cannot be enabled without a PIN fallback`() {
        SecurityPrefs.prefs(context).edit().putBoolean("biometric_enabled", true).commit()

        assertFalse(SecurityPrefs.readState(context).biometricEnabled)
        assertFalse(SecurityPrefs.readState(context).lockConfigured)
        assertFalse(SecurityPrefs.setBiometricEnabled(context, true))

        assertTrue(SecurityPrefs.setPin(context, "1357"))
        assertTrue(SecurityPrefs.setBiometricEnabled(context, true))
        assertTrue(SecurityPrefs.readState(context).biometricEnabled)
        assertTrue(SecurityPrefs.readState(context).hasPin)
    }

    @Test
    fun `fifth failure persists a backoff and success resets it`() {
        assertTrue(SecurityPrefs.setPin(context, "8642"))
        val now = 1_000_000L

        repeat(4) {
            assertEquals(PinUnlockResult.Incorrect(), SecurityPrefs.attemptUnlock(context, "0000", now))
        }
        assertEquals(
            PinUnlockResult.Incorrect(now + 30_000L),
            SecurityPrefs.attemptUnlock(context, "0000", now)
        )
        assertEquals(now + 30_000L, SecurityPrefs.readState(context).pinBlockedUntilMillis)
        assertEquals(
            PinUnlockResult.Blocked(now + 30_000L),
            SecurityPrefs.attemptUnlock(context, "8642", now + 1_000L)
        )
        assertEquals(
            PinUnlockResult.Incorrect(now + 30_001L + 60_000L),
            SecurityPrefs.attemptUnlock(context, "0000", now + 30_001L)
        )

        assertEquals(
            PinUnlockResult.Unlocked,
            SecurityPrefs.attemptUnlock(context, "8642", now + 90_002L)
        )
        assertEquals(0L, SecurityPrefs.readState(context).pinBlockedUntilMillis)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `security observer emits preference changes`() = runTest {
        val states = mutableListOf<SecurityState>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SecurityPrefs.observe(context).take(2).toList(states)
        }

        SecurityPrefs.setHideAmountsInApp(context, true)
        collector.join()

        assertFalse(states.first().hideAmountsInApp)
        assertTrue(states.last().hideAmountsInApp)
    }
}
