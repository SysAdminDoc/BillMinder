package com.sysadmindoc.billminder

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BillMinder ships as one sideloaded build, so the merged manifest is the whole permission story.
 * Asserting it here means a library that starts contributing a permission, or a feature that
 * quietly adds one, breaks a test instead of shipping.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestPermissionsTest {

    /** Declared by the app itself and each tied to a feature a user can reach. */
    private val declaredByTheApp = setOf(
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.SCHEDULE_EXACT_ALARM",
        "android.permission.USE_EXACT_ALARM",
        "android.permission.USE_FULL_SCREEN_INTENT",
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.VIBRATE",
        "android.permission.WAKE_LOCK",
        "android.permission.USE_BIOMETRIC"
    )

    /**
     * Contributed by libraries rather than by this app. USE_FINGERPRINT is androidx.biometric's
     * pre-API-28 path, FOREGROUND_SERVICE comes from ML Kit, and the androidx receiver permission
     * is signature-level and used only inside the app's own process.
     */
    private val libraryContributed = setOf(
        "android.permission.USE_FINGERPRINT",
        "android.permission.FOREGROUND_SERVICE",
        "com.sysadmindoc.billminder.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    )

    /** Dropped from the merged manifest: the app never touches the network. */
    private val network = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE"
    )

    private val location = setOf(
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION"
    )

    private fun declaredPermissions(): Set<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        return info.requestedPermissions.orEmpty().toSet()
    }

    @Test
    fun `no location permission is declared`() {
        val declared = declaredPermissions()
        location.forEach { permission ->
            assertFalse(
                "$permission is declared; home geofencing was removed and nothing replaced it",
                permission in declared
            )
        }
    }

    @Test
    fun `every permission the app depends on is present`() {
        val declared = declaredPermissions()
        declaredByTheApp.forEach { assertTrue("$it is missing", it in declared) }
    }

    @Test
    fun `no network access is requested`() {
        val declared = declaredPermissions()
        network.forEach { permission ->
            assertFalse(
                "$permission is declared; the app is offline and the OCR model is bundled",
                permission in declared
            )
        }
    }

    @Test
    fun `nothing outside the documented set is declared`() {
        val unexpected = declaredPermissions() - declaredByTheApp - libraryContributed
        assertEquals("undocumented permissions in the merged manifest", emptySet<String>(), unexpected)
    }
}
