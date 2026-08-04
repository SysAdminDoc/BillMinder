package com.sysadmindoc.billminder.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.BillRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_DWELL
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = BillRepository(BillDatabase.getDatabase(context).billDao())
                val now = System.currentTimeMillis()
                repository.getAllBillsList()
                    .map { bill ->
                        val nextDue = ReminderScheduler.getNextDueDate(bill)
                        val payment = repository.getPaymentForBillDue(bill.id, nextDue)
                        val daysUntilDue = ((nextDue - now) / (24 * 60 * 60 * 1000L)).toInt()
                        bill to Triple(nextDue, payment == null, daysUntilDue)
                    }
                    .filter { (_, state) -> state.second }
                    .sortedBy { (_, state) -> state.third }
                    .take(3)
                    .forEach { (bill, state) ->
                        NotificationHelper.showReminderNotification(
                            context = context,
                            billId = bill.id,
                            billName = "Home reminder: ${bill.name}",
                            amount = bill.amount,
                            daysUntilDue = state.third,
                            isAutoPay = bill.isAutoPay,
                            nextDueDate = state.first,
                            currency = bill.currency
                        )
                    }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
