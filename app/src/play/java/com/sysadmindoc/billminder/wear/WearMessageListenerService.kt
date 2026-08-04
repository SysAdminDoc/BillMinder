package com.sysadmindoc.billminder.wear

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.sysadmindoc.billminder.notification.ReminderReceiver

class WearMessageListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearProtocol.MARK_PAID_PATH) return

        val billId = messageEvent.data.toString(Charsets.UTF_8).toLongOrNull() ?: return
        sendBroadcast(
            Intent(this, ReminderReceiver::class.java).apply {
                action = "MARK_PAID"
                putExtra("bill_id", billId)
                setPackage(packageName)
            }
        )
    }
}
