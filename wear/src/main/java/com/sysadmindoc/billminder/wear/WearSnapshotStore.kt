package com.sysadmindoc.billminder.wear

import android.content.Context
import com.google.android.gms.wearable.DataMap

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

object WearSnapshotStore {
    private const val PREFS = "wear_snapshot"

    fun read(context: Context): WearBillSnapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WearBillSnapshot(
            billId = prefs.getLong(WearProtocol.KEY_BILL_ID, WearProtocol.NO_BILL),
            name = prefs.getString(WearProtocol.KEY_NAME, "").orEmpty(),
            amount = prefs.getFloat(WearProtocol.KEY_AMOUNT, 0f).toDouble(),
            currency = prefs.getString(WearProtocol.KEY_CURRENCY, "USD").orEmpty(),
            dueDate = prefs.getLong(WearProtocol.KEY_DUE_DATE, 0L)
        )
    }

    fun write(context: Context, dataMap: DataMap) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(WearProtocol.KEY_BILL_ID, dataMap.getLong(WearProtocol.KEY_BILL_ID))
            .putString(WearProtocol.KEY_NAME, dataMap.getString(WearProtocol.KEY_NAME).orEmpty())
            .putFloat(WearProtocol.KEY_AMOUNT, dataMap.getDouble(WearProtocol.KEY_AMOUNT).toFloat())
            .putString(WearProtocol.KEY_CURRENCY, dataMap.getString(WearProtocol.KEY_CURRENCY) ?: "USD")
            .putLong(WearProtocol.KEY_DUE_DATE, dataMap.getLong(WearProtocol.KEY_DUE_DATE))
            .apply()
    }
}
