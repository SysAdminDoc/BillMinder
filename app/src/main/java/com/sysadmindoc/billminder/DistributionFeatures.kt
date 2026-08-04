package com.sysadmindoc.billminder

object DistributionFeatures {
    val includesPlayServices: Boolean
        get() = BuildConfig.FLAVOR == "play"
}
