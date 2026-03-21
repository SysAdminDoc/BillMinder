package com.sysadmindoc.billminder

import android.app.Application
import com.sysadmindoc.billminder.notification.NotificationHelper

class BillMinderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
