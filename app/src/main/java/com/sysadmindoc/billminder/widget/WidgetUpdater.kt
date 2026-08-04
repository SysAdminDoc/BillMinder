package com.sysadmindoc.billminder.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        BillMinderWidget().updateAll(context)
        MonthTotalWidget().updateAll(context)
        LockScreenWidget().updateAll(context)
    }
}
