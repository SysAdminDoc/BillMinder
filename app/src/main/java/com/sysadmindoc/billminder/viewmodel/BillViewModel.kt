package com.sysadmindoc.billminder.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.billminder.data.*
import com.sysadmindoc.billminder.notification.ReminderScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class BillWithStatus(
    val bill: Bill,
    val nextDueDate: Long,
    val daysUntilDue: Int,
    val isPaidThisCycle: Boolean,
    val isOverdue: Boolean
)

data class MonthlySummary(
    val totalDue: Double,
    val totalPaid: Double,
    val remaining: Double,
    val billCount: Int,
    val paidCount: Int,
    val overdueCount: Int
)

class BillViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BillDatabase.getDatabase(application)
    private val repo = BillRepository(db.billDao())

    val bills: StateFlow<List<Bill>> = repo.allBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<Payment>> = repo.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val billsWithStatus: StateFlow<List<BillWithStatus>> = combine(bills, payments) { billList, paymentList ->
        billList.map { bill ->
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val now = System.currentTimeMillis()
            val daysUntil = ((nextDue - now) / (1000 * 60 * 60 * 24)).toInt()
            val isPaid = paymentList.any { it.billId == bill.id && it.dueDate == nextDue }
            val isOverdue = !isPaid && daysUntil < 0
            BillWithStatus(bill, nextDue, daysUntil, isPaid, isOverdue)
        }.sortedWith(compareBy<BillWithStatus> { it.isPaidThisCycle }.thenBy { it.daysUntilDue })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlySummary: StateFlow<MonthlySummary> = billsWithStatus.map { list ->
        val unpaid = list.filter { !it.isPaidThisCycle }
        val paid = list.filter { it.isPaidThisCycle }
        val overdue = list.filter { it.isOverdue }
        val totalDue = list.sumOf { it.bill.amount }
        val totalPaid = paid.sumOf { it.bill.amount }
        MonthlySummary(
            totalDue = totalDue,
            totalPaid = totalPaid,
            remaining = totalDue - totalPaid,
            billCount = list.size,
            paidCount = paid.size,
            overdueCount = overdue.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlySummary(0.0, 0.0, 0.0, 0, 0, 0))

    fun getPaymentsForBill(billId: Long): Flow<List<Payment>> = repo.getPaymentsForBill(billId)

    fun saveBill(bill: Bill) {
        viewModelScope.launch {
            val id = if (bill.id == 0L) {
                repo.insertBill(bill)
            } else {
                repo.updateBill(bill)
                bill.id
            }
            val saved = repo.getBillById(id) ?: return@launch
            if (saved.isEnabled) {
                ReminderScheduler.scheduleReminder(getApplication(), saved)
            } else {
                ReminderScheduler.cancelReminder(getApplication(), saved.id)
            }
        }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            ReminderScheduler.cancelReminder(getApplication(), bill.id)
            repo.deleteBill(bill)
        }
    }

    fun markAsPaid(bill: Bill) {
        viewModelScope.launch {
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val existing = repo.getPaymentForBillDue(bill.id, nextDue)
            if (existing == null) {
                repo.insertPayment(
                    Payment(billId = bill.id, amount = bill.amount, dueDate = nextDue)
                )
            }
            // Dismiss overdue notification
            val nm = getApplication<Application>().getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(bill.id.toInt())
            nm.cancel((bill.id + 20000).toInt())
        }
    }

    fun unmarkAsPaid(bill: Bill) {
        viewModelScope.launch {
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val payment = repo.getPaymentForBillDue(bill.id, nextDue)
            payment?.let { repo.deletePayment(it) }
        }
    }

    suspend fun getBillById(id: Long): Bill? = repo.getBillById(id)
}
