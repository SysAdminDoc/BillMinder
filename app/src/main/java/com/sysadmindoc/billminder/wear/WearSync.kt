package com.sysadmindoc.billminder.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.BillRepository
import com.sysadmindoc.billminder.notification.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class WearBillSnapshot(
    val billId: Long,
    val name: String,
    val amount: Double,
    val currency: String,
    val dueDate: Long
)

object WearProtocol {
    const val SNAPSHOT_PATH = "/billminder/next-due"
    const val MARK_PAID_PATH = "/billminder/mark-paid"
    const val EXTRA_BILL_ID = "bill_id"
    const val NO_BILL = -1L
    const val KEY_BILL_ID = "bill_id"
    const val KEY_NAME = "name"
    const val KEY_AMOUNT = "amount"
    const val KEY_CURRENCY = "currency"
    const val KEY_DUE_DATE = "due_date"
}

object WearSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                val repository = BillRepository(BillDatabase.getDatabase(appContext).billDao())
                var snapshot: WearBillSnapshot? = null
                for (bill in repository.getAllBillsList()) {
                    val dueDate = ReminderScheduler.getNextDueDate(bill)
                    if (repository.getPaymentForBillDue(bill.id, dueDate) == null &&
                        (snapshot == null || dueDate < snapshot!!.dueDate)
                    ) {
                        snapshot = WearBillSnapshot(
                            billId = bill.id,
                            name = bill.name,
                            amount = bill.amount,
                            currency = bill.currency,
                            dueDate = dueDate
                        )
                    }
                }

                val request = PutDataMapRequest.create(WearProtocol.SNAPSHOT_PATH).apply {
                    dataMap.putLong(WearProtocol.KEY_BILL_ID, snapshot?.billId ?: WearProtocol.NO_BILL)
                    dataMap.putString(WearProtocol.KEY_NAME, snapshot?.name.orEmpty())
                    dataMap.putDouble(WearProtocol.KEY_AMOUNT, snapshot?.amount ?: 0.0)
                    dataMap.putString(WearProtocol.KEY_CURRENCY, snapshot?.currency ?: "USD")
                    dataMap.putLong(WearProtocol.KEY_DUE_DATE, snapshot?.dueDate ?: 0L)
                }.asPutDataRequest().setUrgent()

                Wearable.getDataClient(appContext).putDataItem(request)
            }
        }
    }
}
