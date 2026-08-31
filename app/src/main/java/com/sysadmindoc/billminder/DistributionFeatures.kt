package com.sysadmindoc.billminder

/**
 * What each distribution is allowed to ship.
 *
 * Play restricts exact-alarm auto-grant, full-screen intents, and SMS reading to app categories
 * BillMinder does not claim eligibility for, so those permissions are declared only by the F-Droid
 * flavor and the features behind them are hidden everywhere else. Keeping the decision here means
 * one place to check rather than a permission test scattered through the UI.
 */
object DistributionFeatures {

    private val isFdroid: Boolean get() = BuildConfig.FLAVOR == "fdroid"

    val includesPlayServices: Boolean
        get() = BuildConfig.FLAVOR == "play"

    /** Reading the SMS inbox needs READ_SMS, which only the open-source build declares. */
    val canReadSmsInbox: Boolean get() = isFdroid

    /** The alarm-style due screen needs USE_FULL_SCREEN_INTENT, likewise. */
    val canShowFullScreenReminders: Boolean get() = isFdroid
}
