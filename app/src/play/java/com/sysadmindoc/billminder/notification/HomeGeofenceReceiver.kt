package com.sysadmindoc.billminder.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.BillRepository
import com.sysadmindoc.billminder.domain.BillCycles
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
                val repository = BillRepository(BillDatabase.getDatabase(context))
                val payments = repository.getAllPaymentsForExport()
                repository.getAllBillsList()
                    .mapNotNull { bill -> BillCycles.resolve(bill, payments)?.let { bill to it } }
                    .filter { (_, cycle) -> !cycle.isPaid }
                    .sortedBy { (_, cycle) -> cycle.daysUntilDue }
                    .take(3)
                    .forEach { (bill, cycle) ->
                        NotificationHelper.showReminderNotification(
                            context = context,
                            billId = bill.id,
                            billName = "Home reminder: ${bill.name}",
                            amount = bill.amount,
                            daysUntilDue = cycle.daysUntilDue,
                            isAutoPay = bill.isAutoPay,
                            nextDueDate = cycle.dueAt,
                            cycleKey = cycle.cycleKey,
                            currency = bill.currency
                        )
                    }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
