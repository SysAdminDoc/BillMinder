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
 * The merged manifest is what a store reviews, so the permission set is asserted rather than
 * assumed. Adding a restricted permission to the shared manifest breaks this test.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestPermissionsTest {

    private val core = setOf(
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.SCHEDULE_EXACT_ALARM",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.VIBRATE",
        "android.permission.WAKE_LOCK",
        "android.permission.USE_BIOMETRIC"
    )

    /** Permissions a store restricts to app categories BillMinder does not claim. */
    private val restricted = setOf(
        "android.permission.USE_EXACT_ALARM",
        "android.permission.USE_FULL_SCREEN_INTENT",
        "android.permission.READ_SMS"
    )

    /**
     * Contributed by libraries rather than by this app. USE_FINGERPRINT is androidx.biometric's
     * pre-API-28 path, FOREGROUND_SERVICE comes from Play services, and the androidx receiver
     * permission is signature-level and used only inside the app's own process.
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
    fun `no flavor declares a location permission`() {
        val declared = declaredPermissions()
        location.forEach { permission ->
            assertFalse(
                "$permission is declared; home geofencing was removed because Play grants " +
                    "background location only against a reviewed declaration",
                permission in declared
            )
        }
    }

    @Test
    fun `every core permission is present`() {
        val declared = declaredPermissions()
        core.forEach { assertTrue("$it is missing", it in declared) }
    }

    @Test
    fun `restricted permissions belong to the open-source flavor only`() {
        val declared = declaredPermissions()
        if (BuildConfig.FLAVOR == "fdroid") {
            restricted.forEach { assertTrue("$it is missing from the fdroid build", it in declared) }
        } else {
            restricted.forEach { assertFalse("$it must not ship on Play", it in declared) }
        }
    }

    @Test
    fun `the flavor's feature switches match the permissions it declares`() {
        val declared = declaredPermissions()
        assertEquals(
            "android.permission.READ_SMS" in declared,
            DistributionFeatures.canReadSmsInbox
        )
        assertEquals(
            "android.permission.USE_FULL_SCREEN_INTENT" in declared,
            DistributionFeatures.canShowFullScreenReminders
        )
    }

    @Test
    fun `no flavor asks for network access`() {
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
        val allowed = core + libraryContributed +
            if (BuildConfig.FLAVOR == "fdroid") restricted else emptySet()
        val unexpected = declaredPermissions() - allowed
        assertEquals("undocumented permissions in the ${BuildConfig.FLAVOR} manifest", emptySet<String>(), unexpected)
    }
}
