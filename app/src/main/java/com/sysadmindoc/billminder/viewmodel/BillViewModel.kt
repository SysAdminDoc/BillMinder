package com.sysadmindoc.billminder.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.billminder.data.*
import com.sysadmindoc.billminder.notification.ReminderScheduler
import com.sysadmindoc.billminder.security.EncryptedAttachment
import com.sysadmindoc.billminder.security.EncryptedAttachmentStore
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
    val allPaid: Boolean = false,
    val currency: String = "USD"
)

data class ForecastData(
    val next30Days: Double = 0.0,
    val next60Days: Double = 0.0,
    val next90Days: Double = 0.0,
    val next30Bills: Int = 0,
    val next60Bills: Int = 0,
    val next90Bills: Int = 0
)

data class ChartData(
    val categoryBreakdown: List<Pair<BillCategory, Double>> = emptyList(),
    val monthlyTrend: List<Pair<String, Double>> = emptyList(),
    val lifetimeTotal: Double = 0.0,
    val yearlyProjection: Double = 0.0,
    val forecast: ForecastData = ForecastData(),
    val currency: String = "USD",
    val cashFlow: List<MonthlyCashFlow> = emptyList()
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

    private val _displayCurrency = MutableStateFlow(CurrencyPrefs.getDisplayCurrency(application))
    val displayCurrency: StateFlow<String> = _displayCurrency.asStateFlow()
    private val _currencySettingsRevision = MutableStateFlow(0)

    // Undo delete state
    private val _lastDeletedBill = MutableStateFlow<Bill?>(null)
    val lastDeletedBill: StateFlow<Bill?> = _lastDeletedBill
    private var lastDeletedPayees: List<PayeeDraft> = emptyList()

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

    val monthlySummary: StateFlow<MonthlySummary> = combine(bills, payments, _currencySettingsRevision) { billList, paymentList, _ ->
        val targetCurrency = _displayCurrency.value
        val manualRates = CurrencyPrefs.getManualRates(getApplication())
        val toDisplay: (Double, String) -> Double = { amount, currency ->
            CurrencyConverter.convert(amount, currency, targetCurrency, manualRates)
        }
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
        val totalDue = statuses.sumOf { toDisplay(it.bill.amount, it.bill.currency) }
        val totalPaid = paid.sumOf { status ->
            val payment = paymentList.firstOrNull {
                it.billId == status.bill.id && it.dueDate == status.nextDueDate
            }
            if (payment == null) {
                toDisplay(status.bill.amount, status.bill.currency)
            } else {
                toDisplay(payment.amount, payment.currency.ifBlank { status.bill.currency })
            }
        }
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
            allPaid = allPaid,
            currency = targetCurrency
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
            val targetCurrency = _displayCurrency.value
            val manualRates = CurrencyPrefs.getManualRates(getApplication())
            val toDisplay: (Double, String) -> Double = { amount, currency ->
                CurrencyConverter.convert(amount, currency, targetCurrency, manualRates)
            }
            val allBills = repo.getAllBillsForExport()
            val billMap = allBills.associateBy { it.id }
            val allPayments = repo.getAllPaymentsForExport()
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val monthStart = Calendar.getInstance().apply {
                set(year, month, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val monthEnd = Calendar.getInstance().apply {
                set(year, month, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, 1)
            }.timeInMillis

            val categoryBreakdown = allPayments
                .asSequence()
                .filter { it.paidAt >= monthStart && it.paidAt < monthEnd }
                .mapNotNull { payment ->
                    billMap[payment.billId]?.let { bill ->
                        bill.category to toDisplay(payment.amount, payment.currency.ifBlank { bill.currency })
                    }
                }
                .groupBy({ it.first }, { it.second })
                .map { (category, amounts) -> category to amounts.sum() }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }

            val monthlyTrend = mutableListOf<Pair<String, Double>>()
            val monthNames = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            var totalLast12 = 0.0
            var monthsCounted = 0
            for (i in 11 downTo 0) {
                val tCal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.MONTH, -i)
                }
                val m = tCal.get(Calendar.MONTH)
                val y = tCal.get(Calendar.YEAR)
                val start = tCal.timeInMillis
                val end = (tCal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }.timeInMillis
                val total = allPayments
                    .asSequence()
                    .filter { it.paidAt >= start && it.paidAt < end }
                    .sumOf { payment ->
                        val bill = billMap[payment.billId]
                        toDisplay(payment.amount, payment.currency.ifBlank { bill?.currency ?: "USD" })
                    }
                if (i < 6) monthlyTrend.add("${monthNames[m]} ${y % 100}" to total)
                if (total > 0) { totalLast12 += total; monthsCounted++ }
            }

            val lifetimeTotal = allPayments.sumOf { payment ->
                val bill = billMap[payment.billId]
                toDisplay(payment.amount, payment.currency.ifBlank { bill?.currency ?: "USD" })
            }
            val yearlyProjection = if (monthsCounted > 0) (totalLast12 / monthsCounted) * 12 else 0.0

            // Forecast: compute upcoming bills in 30/60/90 days
            val now = System.currentTimeMillis()
            val day30 = now + 30L * 24 * 60 * 60 * 1000
            val day60 = now + 60L * 24 * 60 * 60 * 1000
            val day90 = now + 90L * 24 * 60 * 60 * 1000
            val paidCycles = allPayments
                .asSequence()
                .map { it.billId to it.dueDate }
                .toSet()
            var total30 = 0.0; var total60 = 0.0; var total90 = 0.0
            var count30 = 0; var count60 = 0; var count90 = 0
            allBills.filter { it.isEnabled }.forEach { bill ->
                // Collect all due dates for this bill within 90 days
                var dueDate = ReminderScheduler.getNextDueDate(bill)
                val seen = mutableSetOf<Long>()
                while (dueDate <= day90 && seen.add(dueDate)) {
                    if (dueDate >= now && (bill.id to dueDate) !in paidCycles) {
                        val amount = toDisplay(bill.amount, bill.currency)
                        if (dueDate <= day30) { total30 += amount; count30++ }
                        if (dueDate <= day60) { total60 += amount; count60++ }
                        total90 += amount; count90++
                    }
                    val nextDue = ReminderScheduler.getNextDueDateAfter(bill, dueDate)
                    if (nextDue == null || nextDue <= dueDate) break
                    dueDate = nextDue
                }
            }
            val forecast = ForecastData(total30, total60, total90, count30, count60, count90)
            val cashFlow = CashFlowProjection.build(
                allBills,
                allPayments,
                now,
                toDisplay
            )

            _chartData.value = ChartData(
                categoryBreakdown,
                monthlyTrend,
                lifetimeTotal,
                yearlyProjection,
                forecast,
                targetCurrency,
                cashFlow
            )
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setSortMode(mode: SortMode) { _sortMode.value = mode }
    fun setFilterCategory(category: BillCategory?) { _filterCategory.value = category }

    fun setDisplayCurrency(currency: String) {
        CurrencyPrefs.setDisplayCurrency(getApplication(), currency)
        _displayCurrency.value = CurrencyPrefs.getDisplayCurrency(getApplication())
        _currencySettingsRevision.update { it + 1 }
        loadChartData()
    }

    fun setManualRate(currency: String, rate: Double?) {
        CurrencyPrefs.setManualRate(getApplication(), currency, rate)
        _currencySettingsRevision.update { it + 1 }
        loadChartData()
    }

    fun getManualRates(): Map<String, Double> = CurrencyPrefs.getManualRates(getApplication())

    fun convertToDisplay(amount: Double, currency: String): Double =
        CurrencyConverter.convert(
            amount,
            currency,
            _displayCurrency.value,
            CurrencyPrefs.getManualRates(getApplication())
        )

    fun getPaymentsForBill(billId: Long): Flow<List<Payment>> = repo.getPaymentsForBill(billId)

    fun saveBill(bill: Bill, payees: List<PayeeDraft>? = null) {
        viewModelScope.launch {
            val normalizedBill = bill.copy(
                name = MerchantNormalizer.normalize(bill.name),
                currency = CurrencyCatalog.find(bill.currency).code
            )
            val id = if (normalizedBill.id == 0L) {
                repo.insertBill(normalizedBill)
            } else {
                repo.updateBill(normalizedBill)
                normalizedBill.id
            }
            val saved = repo.getBillById(id) ?: return@launch
            if (payees != null) {
                repo.replacePayees(saved.id, payees)
            }
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
            lastDeletedPayees = repo.getPayeesForBill(bill.id).map { PayeeDraft(it.name, it.sharePercent) }
            repo.deletePayeesForBill(bill.id)
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
            repo.replacePayees(id, lastDeletedPayees)
            val saved = repo.getBillById(id) ?: return@launch
            if (saved.isEnabled) {
                ReminderScheduler.scheduleReminder(getApplication(), saved)
            }
            _lastDeletedBill.value = null
            lastDeletedPayees = emptyList()
        }
    }

    fun duplicateBill(bill: Bill) {
        viewModelScope.launch {
            val sourcePayees = repo.getPayeesForBill(bill.id)
            val copy = bill.copy(
                id = 0,
                name = "${bill.name} (Copy)",
                createdAt = System.currentTimeMillis()
            )
            val id = repo.insertBill(copy)
            repo.replacePayees(id, sourcePayees.map { PayeeDraft(it.name, it.sharePercent) })
            val saved = repo.getBillById(id) ?: return@launch
            if (saved.isEnabled) {
                ReminderScheduler.scheduleReminder(getApplication(), saved)
            }
            _snackbarMessage.emit("${bill.name} duplicated")
        }
    }

    suspend fun getPayeesForBill(billId: Long): List<BillPayee> = repo.getPayeesForBill(billId)

    fun markAsPaid(
        bill: Bill,
        customAmount: Double? = null,
        confirmationNumber: String = "",
        attachment: EncryptedAttachment? = null
    ) {
        viewModelScope.launch {
            val nextDue = ReminderScheduler.getNextDueDate(bill)
            val existing = repo.getPaymentForBillDue(bill.id, nextDue)
            if (existing == null) {
                repo.insertPayment(
                    Payment(
                        billId = bill.id,
                        amount = customAmount ?: bill.amount,
                        dueDate = nextDue,
                        confirmationNumber = confirmationNumber,
                        attachmentName = attachment?.displayName.orEmpty(),
                        attachmentFile = attachment?.fileName.orEmpty(),
                        attachmentMime = attachment?.mimeType ?: "application/octet-stream",
                        currency = bill.currency
                    )
                )
            } else if (attachment != null) {
                EncryptedAttachmentStore.delete(getApplication(), attachment.fileName)
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
            payment?.let {
                EncryptedAttachmentStore.delete(getApplication(), it.attachmentFile)
                repo.deletePayment(it)
            }
            loadChartData()
        }
    }

    suspend fun getBillById(id: Long): Bill? = repo.getBillById(id)

    suspend fun getLifetimeSpending(billId: Long): Double {
        val bill = repo.getBillById(billId) ?: return 0.0
        val payments = repo.getPaymentsForBillList(billId)
        val manualRates = CurrencyPrefs.getManualRates(getApplication())
        return payments.sumOf {
            CurrencyConverter.convert(
                it.amount,
                it.currency.ifBlank { bill.currency },
                bill.currency,
                manualRates
            )
        }
    }

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

    fun exportYearEndCsv(uri: Uri, year: Int) {
        viewModelScope.launch {
            BackupManager.exportYearEndCsv(getApplication(), uri, repo, year)
        }
    }
}
