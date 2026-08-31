package com.sysadmindoc.billminder.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sysadmindoc.billminder.data.*
import com.sysadmindoc.billminder.domain.BillCycles
import com.sysadmindoc.billminder.domain.CycleEngine
import com.sysadmindoc.billminder.notification.ReminderScheduler
import com.sysadmindoc.billminder.notification.ReminderPrefs
import com.sysadmindoc.billminder.security.EncryptedAttachment
import com.sysadmindoc.billminder.security.EncryptedAttachmentStore
import com.sysadmindoc.billminder.wear.WearSync
import com.sysadmindoc.billminder.widget.WidgetUpdater
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

data class BillWithStatus(
    val bill: Bill,
    val nextDueDate: Long,
    val daysUntilDue: Int,
    val isPaidThisCycle: Boolean,
    val isOverdue: Boolean,
    val cycleKey: String = "",
    val cycleDate: LocalDate? = null
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
    private val repo = BillRepository(db)

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
    private var pendingDelete: BillGraph? = null

    /** One resolution path for every list, summary, and detail surface in the app. */
    private fun statusesFor(billList: List<Bill>, paymentList: List<Payment>): List<BillWithStatus> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val paidByBill = BillCycles.paidKeys(paymentList)
        return billList.mapNotNull { bill ->
            val cycle = BillCycles.resolve(
                bill = bill,
                paidKeys = paidByBill[bill.id].orEmpty(),
                payments = paymentList,
                today = today,
                zone = zone
            ) ?: return@mapNotNull null
            BillWithStatus(
                bill = bill,
                nextDueDate = cycle.dueAt,
                daysUntilDue = cycle.daysUntilDue,
                isPaidThisCycle = cycle.isPaid,
                isOverdue = cycle.isOverdue,
                cycleKey = cycle.cycleKey,
                cycleDate = cycle.date
            )
        }
    }

    private suspend fun refreshExternalSurfaces() {
        WearSync.sync(getApplication())
        WidgetUpdater.updateAll(getApplication())
    }

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

        val mapped = statusesFor(filtered, paymentList)

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
        val statuses = statusesFor(billList, paymentList)
        val paid = statuses.filter { it.isPaidThisCycle }
        val overdue = statuses.filter { it.isOverdue }
        val totalDue = statuses.sumOf { toDisplay(it.bill.amount, it.bill.currency) }
        val totalPaid = paid.sumOf { status ->
            val payment = paymentList.firstOrNull {
                it.billId == status.bill.id && it.cycleKey == status.cycleKey
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

            // Forecast: upcoming unpaid occurrences within 30/60/90 days
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val paidByBill = BillCycles.paidKeys(allPayments)
            var total30 = 0.0; var total60 = 0.0; var total90 = 0.0
            var count30 = 0; var count60 = 0; var count90 = 0
            allBills.filter { it.isEnabled }.forEach { bill ->
                val amount = toDisplay(bill.amount, bill.currency)
                BillCycles.unpaidOccurrences(
                    bill = bill,
                    paidKeys = paidByBill[bill.id].orEmpty(),
                    start = today,
                    endInclusive = today.plusDays(90),
                    zone = zone
                ).forEach { date ->
                    val daysOut = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                    if (daysOut <= 30) { total30 += amount; count30++ }
                    if (daysOut <= 60) { total60 += amount; count60++ }
                    total90 += amount; count90++
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

    fun setVacationMode(enabled: Boolean) {
        ReminderPrefs.setVacationMode(getApplication(), enabled)
        viewModelScope.launch {
            ReminderScheduler.scheduleAllReminders(getApplication(), repo.getAllBillsList())
        }
    }

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
            val saved = try {
                repo.saveBillWithPayees(normalizedBill, payees)
            } catch (error: Exception) {
                _snackbarMessage.emit("Could not save ${normalizedBill.name}")
                return@launch
            } ?: return@launch
            if (saved.isEnabled) {
                ReminderScheduler.scheduleReminder(getApplication(), saved)
            } else {
                ReminderScheduler.cancelReminder(getApplication(), saved.id)
            }
            refreshExternalSurfaces()
        }
    }

    /**
     * Permanently removes the bill, its payees, and its payment history in one transaction. The
     * graph is held until the undo window closes; only then are the receipt bytes destroyed.
     */
    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            purgePendingDelete()
            ReminderScheduler.cancelReminder(getApplication(), bill.id)
            val graph = try {
                repo.deleteBillGraph(bill.id)
            } catch (error: Exception) {
                _snackbarMessage.emit("Could not delete ${bill.name}")
                return@launch
            }
            if (graph == null) return@launch
            pendingDelete = graph
            _lastDeletedBill.value = graph.bill
            refreshExternalSurfaces()
            loadChartData()
            _snackbarMessage.emit("${bill.name} deleted")
        }
    }

    fun undoDelete() {
        val graph = pendingDelete ?: return
        viewModelScope.launch {
            val restored = try {
                repo.restoreBillGraph(graph)
            } catch (error: Exception) {
                _snackbarMessage.emit("Could not restore ${graph.bill.name}")
                return@launch
            } ?: return@launch
            if (restored.isEnabled) {
                ReminderScheduler.scheduleReminder(getApplication(), restored)
            }
            pendingDelete = null
            _lastDeletedBill.value = null
            refreshExternalSurfaces()
            loadChartData()
        }
    }

    /** Called when the undo window closes without being used, making the delete final. */
    fun confirmPendingDelete() = purgePendingDelete()

    /** Drops the undo snapshot and destroys the receipt bytes it was protecting. */
    private fun purgePendingDelete() {
        val graph = pendingDelete ?: return
        pendingDelete = null
        _lastDeletedBill.value = null
        graph.attachmentFiles.forEach { EncryptedAttachmentStore.delete(getApplication(), it) }
    }

    override fun onCleared() {
        purgePendingDelete()
        super.onCleared()
    }

    fun duplicateBill(bill: Bill) {
        viewModelScope.launch {
            val sourcePayees = repo.getPayeesForBill(bill.id)
            val copy = bill.copy(
                id = 0,
                name = "${bill.name} (Copy)",
                createdAt = System.currentTimeMillis()
            )
            val saved = try {
                repo.saveBillWithPayees(copy, sourcePayees.map { PayeeDraft(it.name, it.sharePercent) })
            } catch (error: Exception) {
                _snackbarMessage.emit("Could not duplicate ${bill.name}")
                return@launch
            } ?: return@launch
            if (saved.isEnabled) {
                ReminderScheduler.scheduleReminder(getApplication(), saved)
            }
            refreshExternalSurfaces()
            _snackbarMessage.emit("${bill.name} duplicated")
        }
    }

    suspend fun getPayeesForBill(billId: Long): List<BillPayee> = repo.getPayeesForBill(billId)

    fun markAsPaid(
        bill: Bill,
        customAmount: Double? = null,
        confirmationNumber: String = "",
        attachment: EncryptedAttachment? = null,
        paidAt: Long? = null
    ) {
        viewModelScope.launch {
            val cycle = repo.currentCycleFor(bill)
            val inserted = if (cycle == null) {
                -1L
            } else {
                repo.insertPayment(
                    Payment(
                        billId = bill.id,
                        amount = customAmount ?: bill.amount,
                        paidAt = paidAt ?: System.currentTimeMillis(),
                        dueDate = CycleEngine.dueInstant(cycle),
                        confirmationNumber = confirmationNumber,
                        attachmentName = attachment?.displayName.orEmpty(),
                        attachmentFile = attachment?.fileName.orEmpty(),
                        attachmentMime = attachment?.mimeType ?: "application/octet-stream",
                        currency = bill.currency,
                        cycleKey = CycleEngine.cycleKey(cycle)
                    )
                )
            }
            if (inserted <= 0L && attachment != null) {
                EncryptedAttachmentStore.delete(getApplication(), attachment.fileName)
            }
            val nm = getApplication<Application>().getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(bill.id.toInt())
            nm.cancel((bill.id + 20000).toInt())
            refreshExternalSurfaces()
            loadChartData()
        }
    }

    fun unmarkAsPaid(bill: Bill) {
        viewModelScope.launch {
            val cycle = repo.currentCycleFor(bill)
            val payment = cycle?.let { repo.getPaymentForCycle(bill.id, CycleEngine.cycleKey(it)) }
            payment?.let {
                EncryptedAttachmentStore.delete(getApplication(), it.attachmentFile)
                repo.deletePayment(it)
            }
            refreshExternalSurfaces()
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
            refreshExternalSurfaces()
            loadChartData()
            onComplete(count)
        }
    }

    fun previewCsv(uri: Uri, onComplete: (CsvTable?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val table = withContext(Dispatchers.IO) {
                    CsvImport.read(getApplication(), uri)
                }
                if (table == null || table.headers.isEmpty()) {
                    onComplete(null, "The CSV file is empty or unreadable")
                } else {
                    onComplete(table, null)
                }
            } catch (error: Exception) {
                onComplete(null, error.message ?: "Unable to read CSV")
            }
        }
    }

    fun importCsv(uri: Uri, mapping: CsvImportMapping, onComplete: (CsvImportResult?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    BackupManager.importCsv(getApplication(), uri, repo, mapping)
                }
                ReminderScheduler.scheduleAllReminders(getApplication(), repo.getAllBillsList())
                refreshExternalSurfaces()
                loadChartData()
                onComplete(result, null)
            } catch (error: Exception) {
                onComplete(null, error.message ?: "Unable to import CSV")
            }
        }
    }

    fun scanSms(onComplete: (List<SmsBillCandidate>, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val candidates = withContext(Dispatchers.IO) {
                    SmsBillReader.readRecent(getApplication())
                }
                onComplete(candidates, null)
            } catch (error: SecurityException) {
                onComplete(emptyList(), "SMS permission is required to scan messages")
            } catch (error: Exception) {
                onComplete(emptyList(), error.message ?: "Unable to scan SMS")
            }
        }
    }

    fun importSmsCandidate(candidate: SmsBillCandidate) {
        val bill = Bill(
            name = candidate.name,
            amount = candidate.amount,
            dueDay = candidate.dueDate.dayOfMonth,
            dueMonth = candidate.dueDate.monthValue - 1,
            dueYear = candidate.dueDate.year,
            recurrence = Recurrence.ONE_TIME,
            currency = candidate.currency,
            notes = "Proposed from SMS from ${candidate.sender}"
        )
        saveBill(bill, emptyList())
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            BackupManager.exportCsv(getApplication(), uri, repo)
        }
    }

    fun exportInterchange(uri: Uri, format: InterchangeFormat) {
        viewModelScope.launch {
            BackupManager.exportInterchangeCsv(
                context = getApplication(),
                uri = uri,
                repo = repo,
                format = format,
                targetCurrency = _displayCurrency.value,
                manualRates = CurrencyPrefs.getManualRates(getApplication())
            )
        }
    }

    fun exportYearEndCsv(uri: Uri, year: Int) {
        viewModelScope.launch {
            BackupManager.exportYearEndCsv(getApplication(), uri, repo, year)
        }
    }
}
