package com.sysadmindoc.billminder

import android.app.Application
import com.sysadmindoc.billminder.notification.NotificationHelper
import com.sysadmindoc.billminder.wear.WearSync

class BillMinderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        WearSync.sync(this)
    }
}
