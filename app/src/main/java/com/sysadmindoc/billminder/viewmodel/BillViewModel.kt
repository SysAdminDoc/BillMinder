package com.sysadmindoc.billminder.viewmodel

import android.app.Application
import android.net.Uri
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
    val overdueCount: Int,
    val nextDueBill: BillWithStatus? = null,
    val allPaid: Boolean = false
)

data class ChartData(
    val categoryBreakdown: List<Pair<BillCategory, Double>> = emptyList(),
    val monthlyTrend: List<Pair<String, Double>> = emptyList(),
    val lifetimeTotal: Double = 0.0,
    val yearlyProjection: Double = 0.0
)

class BillViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BillDatabase.getDatabase(application)
    private val repo = BillRepository(db.billDao())

    // Search & filter state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortMode = MutableStateFlow(SortMode.DUE_DATE)
    val sortMode: StateFlow<SortMode> = _sortMode

    private val _filterCategory = MutableStateFlow<BillCategory?>(null)
    val filterCategory: StateFlow<BillCategory?> = _filterCategory

    // Undo delete state
    private val _lastDeletedBill = MutableStateFlow<Bill?>(null)
    val lastDeletedBill: StateFlow<Bill?> = _lastDeletedBill

    // Snackbar events
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage

    val bills: StateFlow<List<Bill>> = repo.allBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<Payment>> = repo.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val billsWithStatus: StateFlow<List<BillWithStatus>> = combine(
        bills, payments, _searchQuery, _sortMode, _filterCategory
    ) { billList, paymentList, query, sort, catFilter ->
        var filtered = billList

        if (query.isNotBlank()) {
            val q = query.lowercase()
            filtered = filtered.filter {
                it.name.lowercase().contains(q) ||
                it.notes.lowercase().contains(q) ||
                it.tags.lowercase().contains(q) ||
                it.category.label.lowercase().contains(q)
            }
        }

        if (catFilter != null) {
            filtered = filtered.filter { it.category == catFilter }
        }

        val mapped = filtered.map { bill ->
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val now = System.currentTimeMillis()
            val daysUntil = ((nextDue - now) / (1000 * 60 * 60 * 24)).toInt()
            val isPaid = paymentList.any { it.billId == bill.id && it.dueDate == nextDue }
            val isOverdue = !isPaid && daysUntil < 0
            BillWithStatus(bill, nextDue, daysUntil, isPaid, isOverdue)
        }

        when (sort) {
            SortMode.DUE_DATE -> mapped.sortedWith(
                compareBy<BillWithStatus> { it.isPaidThisCycle }.thenBy { it.daysUntilDue }
            )
            SortMode.AMOUNT_ASC -> mapped.sortedBy { it.bill.amount }
            SortMode.AMOUNT_DESC -> mapped.sortedByDescending { it.bill.amount }
            SortMode.NAME_ASC -> mapped.sortedBy { it.bill.name.lowercase() }
            SortMode.NAME_DESC -> mapped.sortedByDescending { it.bill.name.lowercase() }
            SortMode.CATEGORY -> mapped.sortedBy { it.bill.category.ordinal }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlySummary: StateFlow<MonthlySummary> = combine(bills, payments) { billList, paymentList ->
        val statuses = billList.map { bill ->
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val now = System.currentTimeMillis()
            val daysUntil = ((nextDue - now) / (1000 * 60 * 60 * 24)).toInt()
            val isPaid = paymentList.any { it.billId == bill.id && it.dueDate == nextDue }
            val isOverdue = !isPaid && daysUntil < 0
            BillWithStatus(bill, nextDue, daysUntil, isPaid, isOverdue)
        }
        val paid = statuses.filter { it.isPaidThisCycle }
        val overdue = statuses.filter { it.isOverdue }
        val totalDue = statuses.sumOf { it.bill.amount }
        val totalPaid = paid.sumOf { it.bill.amount }
        val nextDue = statuses.filter { !it.isPaidThisCycle && !it.isOverdue }
            .minByOrNull { it.daysUntilDue }
        val allPaid = statuses.isNotEmpty() && paid.size == statuses.size
        MonthlySummary(
            totalDue = totalDue,
            totalPaid = totalPaid,
            remaining = totalDue - totalPaid,
            billCount = statuses.size,
            paidCount = paid.size,
            overdueCount = overdue.size,
            nextDueBill = nextDue,
            allPaid = allPaid
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlySummary(0.0, 0.0, 0.0, 0, 0, 0))

    // Chart data
    private val _chartData = MutableStateFlow(ChartData())
    val chartData: StateFlow<ChartData> = _chartData

    init {
        loadChartData()
    }

    fun loadChartData() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)

            val categoryBreakdown = repo.getSpendingByCategory(year, month)
                .map { it.category to it.total }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }

            val monthlyTrend = mutableListOf<Pair<String, Double>>()
            val monthNames = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            var totalLast12 = 0.0
            var monthsCounted = 0
            for (i in 11 downTo 0) {
                val tCal = Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
                val m = tCal.get(Calendar.MONTH)
                val y = tCal.get(Calendar.YEAR)
                val total = repo.getMonthlySpendingTotal(y, m)
                if (i < 6) monthlyTrend.add("${monthNames[m]} ${y % 100}" to total)
                if (total > 0) { totalLast12 += total; monthsCounted++ }
            }

            val lifetimeTotal = repo.getTotalLifetimeSpending()
            val yearlyProjection = if (monthsCounted > 0) (totalLast12 / monthsCounted) * 12 else 0.0

            _chartData.value = ChartData(categoryBreakdown, monthlyTrend, lifetimeTotal, yearlyProjection)
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSortMode(mode: SortMode) { _sortMode.value = mode }
    fun setFilterCategory(category: BillCategory?) { _filterCategory.value = category }

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
            _lastDeletedBill.value = bill
            _snackbarMessage.emit("${bill.name} deleted")
        }
    }

    fun undoDelete() {
        val bill = _lastDeletedBill.value ?: return
        viewModelScope.launch {
            val restored = bill.copy(id = 0)
            val id = repo.insertBill(restored)
            val saved = repo.getBillById(id) ?: return@launch
            if (saved.isEnabled) {
                ReminderScheduler.scheduleReminder(getApplication(), saved)
            }
            _lastDeletedBill.value = null
        }
    }

    fun duplicateBill(bill: Bill) {
        viewModelScope.launch {
            val copy = bill.copy(
                id = 0,
                name = "${bill.name} (Copy)",
                createdAt = System.currentTimeMillis()
            )
            val id = repo.insertBill(copy)
            val saved = repo.getBillById(id) ?: return@launch
            if (saved.isEnabled) {
                ReminderScheduler.scheduleReminder(getApplication(), saved)
            }
            _snackbarMessage.emit("${bill.name} duplicated")
        }
    }

    fun markAsPaid(bill: Bill, customAmount: Double? = null, confirmationNumber: String = "") {
        viewModelScope.launch {
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val existing = repo.getPaymentForBillDue(bill.id, nextDue)
            if (existing == null) {
                repo.insertPayment(
                    Payment(
                        billId = bill.id,
                        amount = customAmount ?: bill.amount,
                        dueDate = nextDue,
                        confirmationNumber = confirmationNumber
                    )
                )
            }
            val nm = getApplication<Application>().getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(bill.id.toInt())
            nm.cancel((bill.id + 20000).toInt())
            loadChartData()
        }
    }

    fun unmarkAsPaid(bill: Bill) {
        viewModelScope.launch {
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val payment = repo.getPaymentForBillDue(bill.id, nextDue)
            payment?.let { repo.deletePayment(it) }
            loadChartData()
        }
    }

    suspend fun getBillById(id: Long): Bill? = repo.getBillById(id)

    suspend fun getLifetimeSpending(billId: Long): Double = repo.getLifetimeSpending(billId)

    suspend fun getOnTimeStreak(billId: Long): Int = repo.getOnTimeStreak(billId)

    // Backup/restore
    fun exportJson(uri: Uri) {
        viewModelScope.launch {
            BackupManager.exportJson(getApplication(), uri, repo)
        }
    }

    fun importJson(uri: Uri, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = BackupManager.importJson(getApplication(), uri, repo)
            val bills = repo.getAllBillsList()
            ReminderScheduler.scheduleAllReminders(getApplication(), bills)
            loadChartData()
            onComplete(count)
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            BackupManager.exportCsv(getApplication(), uri, repo)
        }
    }
}
