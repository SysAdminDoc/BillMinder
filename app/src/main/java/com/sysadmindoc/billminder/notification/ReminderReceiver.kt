package com.sysadmindoc.billminder.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.BillRepository
import com.sysadmindoc.billminder.data.Payment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val db = BillDatabase.getDatabase(context)
        val repo = BillRepository(db.billDao())

        when (intent.action) {
            "BILL_REMINDER" -> handleReminder(context, intent, repo)
            "MARK_PAID" -> handleMarkPaid(context, intent, repo)
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                rescheduleAll(context, repo)
            }
        }
    }

    private fun handleReminder(context: Context, intent: Intent, repo: BillRepository) {
        val requestCode = intent.getLongExtra("request_code", -1)
        val daysBeforeDue = intent.getIntExtra("days_before_due", 1)
        val billId = if (requestCode >= 50000) requestCode - 50000 else requestCode

        CoroutineScope(Dispatchers.IO).launch {
            val bill = repo.getBillById(billId) ?: return@launch
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val payment = repo.getPaymentForBillDue(bill.id, nextDue)

            if (payment == null) {
                NotificationHelper.showReminderNotification(
                    context = context,
                    billId = bill.id,
                    billName = bill.name,
                    amount = bill.amount,
                    daysUntilDue = daysBeforeDue,
                    isAutoPay = bill.isAutoPay
                )
            }

            // Reschedule for next occurrence
            ReminderScheduler.scheduleReminder(context, bill)
        }
    }

    private fun handleMarkPaid(context: Context, intent: Intent, repo: BillRepository) {
        val billId = intent.getLongExtra("bill_id", -1)
        val amount = intent.getDoubleExtra("amount", 0.0)
        if (billId == -1L) return

        CoroutineScope(Dispatchers.IO).launch {
            val bill = repo.getBillById(billId) ?: return@launch
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            repo.insertPayment(
                Payment(
                    billId = billId,
                    amount = amount,
                    dueDate = nextDue
                )
            )
            // Dismiss notification
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(billId.toInt())
            nm.cancel((billId + 20000).toInt())
        }
    }

    private fun rescheduleAll(context: Context, repo: BillRepository) {
        CoroutineScope(Dispatchers.IO).launch {
            val bills = repo.getAllBillsList()
            ReminderScheduler.scheduleAllReminders(context, bills)
        }
    }
}
