package com.sysadmindoc.billminder.data

import androidx.room.withTransaction
import com.sysadmindoc.billminder.domain.CycleEngine
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * A bill and everything that hangs off it, captured before a delete so the exact same graph can be
 * restored. Attachment file names travel with it, so their bytes are only purged once the undo
 * window has closed.
 */
data class BillGraph(
    val bill: Bill,
    val payees: List<BillPayee>,
    val payments: List<Payment>
) {
    val attachmentFiles: List<String>
        get() = payments.map { it.attachmentFile }.filter { it.isNotBlank() }
}

data class BackupGraphSnapshot(
    val bills: List<Bill>,
    val payments: List<Payment>,
    val payees: List<BillPayee>
)

class BillRepository(private val database: BillDatabase) {

    private val dao: BillDao = database.billDao()

    val allBills: Flow<List<Bill>> = dao.getAllBills()
    val allPayments: Flow<List<Payment>> = dao.getAllPayments()

    fun searchBills(query: String): Flow<List<Bill>> = dao.searchBills(query)

    fun getBillsByCategory(category: BillCategory): Flow<List<Bill>> =
        dao.getBillsByCategory(category)

    fun getPaymentsForBill(billId: Long): Flow<List<Payment>> =
        dao.getPaymentsForBill(billId)

    suspend fun getPaymentsForBillList(billId: Long): List<Payment> =
        dao.getPaymentsForBillList(billId)

    fun getPaymentsForMonth(year: Int, month: Int): Flow<List<Payment>> {
        val (start, end) = getMonthRange(year, month)
        return dao.getPaymentsForMonth(start, end)
    }

    suspend fun getBillById(id: Long): Bill? = dao.getBillById(id)

    suspend fun insertBill(bill: Bill): Long = dao.insertBill(CycleEngine.normalize(bill))

    suspend fun updateBill(bill: Bill) = dao.updateBill(CycleEngine.normalize(bill))

    /**
     * Writes a bill and its payee split as one unit. A failure part-way through leaves neither the
     * bill nor the split changed.
     */
    suspend fun saveBillWithPayees(bill: Bill, payees: List<PayeeDraft>?): Bill? =
        database.withTransaction {
            val normalized = CycleEngine.normalize(bill)
            val id = if (normalized.id == 0L) {
                dao.insertBill(normalized)
            } else {
                dao.updateBill(normalized)
                normalized.id
            }
            if (payees != null) {
                dao.deletePayeesForBill(id)
                if (payees.isNotEmpty()) {
                    dao.insertPayees(
                        payees.map { BillPayee(billId = id, name = it.name, sharePercent = it.sharePercent) }
                    )
                }
            }
            dao.getBillById(id)
        }

    /**
     * Permanently removes a bill together with its payees and payment history, and hands back the
     * complete graph so the caller can restore it or clean up receipt bytes.
     */
    suspend fun deleteBillGraph(billId: Long): BillGraph? = database.withTransaction {
        val bill = dao.getBillById(billId) ?: return@withTransaction null
        val graph = BillGraph(
            bill = bill,
            payees = dao.getPayeesForBill(billId),
            payments = dao.getPaymentsForBillList(billId)
        )
        // Payees and payments cascade, but delete them explicitly so the outcome does not depend
        // on foreign keys being enforced by the connection.
        dao.deletePayeesForBill(billId)
        dao.deletePaymentsForBill(billId)
        dao.deleteBillById(billId)
        graph
    }

    /** Puts a deleted bill back exactly as it was, original identifiers included. */
    suspend fun restoreBillGraph(graph: BillGraph): Bill? = database.withTransaction {
        dao.insertBill(graph.bill)
        if (graph.payees.isNotEmpty()) dao.insertPayees(graph.payees)
        graph.payments.forEach { dao.upsertPayment(it) }
        dao.getBillById(graph.bill.id)
    }

    suspend fun deleteBill(bill: Bill) = dao.deleteBill(bill)

    suspend fun deleteBillById(id: Long) = dao.deleteBillById(id)

    suspend fun insertPayment(payment: Payment): Long = dao.insertPayment(payment)

    suspend fun deletePayment(payment: Payment) = dao.deletePayment(payment)

    suspend fun getPaymentForCycle(billId: Long, cycleKey: String): Payment? =
        dao.getPaymentForCycle(billId, cycleKey)

    suspend fun getPaidCycleKeys(billId: Long): Set<String> =
        dao.getPaidCycleKeys(billId).toSet()

    /** The occurrence [bill] is currently sitting on: the oldest unpaid one, else the next. */
    suspend fun currentCycleFor(
        bill: Bill,
        today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate? {
        val paid = getPaidCycleKeys(bill.id)
        return CycleEngine.currentCycle(bill, today, { it in paid }, zone)
    }

    suspend fun getAllBillsList(): List<Bill> = dao.getAllBillsList()

    suspend fun getAllBillsForExport(): List<Bill> = dao.getAllBillsForExport()

    suspend fun getAllPaymentsForExport(): List<Payment> = dao.getAllPaymentsForExport()

    suspend fun getAllPayeesForExport(): List<BillPayee> = dao.getAllPayeesForExport()

    /** Captures one internally consistent graph for a portable backup. */
    suspend fun snapshotForBackup(): BackupGraphSnapshot = database.withTransaction {
        BackupGraphSnapshot(
            bills = dao.getAllBillsForExport(),
            payments = dao.getAllPaymentsForExport(),
            payees = dao.getAllPayeesForExport()
        )
    }

    /**
     * Restores an already validated backup graph in one Room transaction. The callback runs after
     * every row has been accepted but before Room commits, allowing prepared receipt files and
     * preferences to join the same rollback boundary.
     */
    suspend fun restoreBackupGraph(
        bills: List<Bill>,
        payments: List<Payment>,
        payees: List<BillPayee>,
        policy: BackupRestorePolicy,
        attachmentNamesByPaymentId: Map<Long, String>,
        beforeCommit: suspend () -> Unit
    ): BackupRestoreResult = database.withTransaction {
        if (policy == BackupRestorePolicy.REPLACE) {
            dao.deleteAllPayees()
            dao.deleteAllPayments()
            dao.deleteAllBills()
        }

        val billIdMap = mutableMapOf<Long, Long>()
        bills.sortedBy { it.id }.forEach { bill ->
            val restored = if (policy == BackupRestorePolicy.MERGE) bill.copy(id = 0L) else bill
            val insertedId = dao.insertBill(CycleEngine.normalize(restored))
            check(insertedId > 0L) { "Unable to restore bill ${bill.id}" }
            billIdMap[bill.id] = if (policy == BackupRestorePolicy.MERGE) insertedId else bill.id
        }

        if (payees.isNotEmpty()) {
            dao.insertPayees(
                payees.sortedBy { it.id }.map { payee ->
                    BillPayee(
                        id = if (policy == BackupRestorePolicy.MERGE) 0L else payee.id,
                        billId = billIdMap.getValue(payee.billId),
                        name = payee.name,
                        sharePercent = payee.sharePercent
                    )
                }
            )
        }

        payments.sortedBy { it.id }.forEach { payment ->
            val restored = payment.copy(
                id = if (policy == BackupRestorePolicy.MERGE) 0L else payment.id,
                billId = billIdMap.getValue(payment.billId),
                attachmentFile = attachmentNamesByPaymentId[payment.id].orEmpty()
            )
            check(dao.insertPayment(restored) > 0L) {
                "Unable to restore payment ${payment.id}"
            }
        }

        beforeCommit()
        BackupRestoreResult(
            bills = bills.size,
            payments = payments.size,
            payees = payees.size,
            receipts = attachmentNamesByPaymentId.size,
            policy = policy
        )
    }

    suspend fun getLifetimeSpending(billId: Long): Double = dao.getLifetimeSpending(billId)

    suspend fun getTotalLifetimeSpending(): Double = dao.getTotalLifetimeSpending()

    suspend fun getSpendingByCategory(year: Int, month: Int): List<CategorySpending> {
        val (start, end) = getMonthRange(year, month)
        return dao.getSpendingByCategory(start, end)
    }

    suspend fun getMonthlySpendingTotal(year: Int, month: Int): Double {
        val (start, end) = getMonthRange(year, month)
        return dao.getMonthlySpendingTotal(start, end)
    }

    suspend fun getOnTimeStreak(billId: Long): Int {
        val payments = dao.getPaymentHistoryForStreak(billId)
        var streak = 0
        for (p in payments) {
            if (p.paidAt <= p.dueDate) streak++ else break
        }
        return streak
    }

    suspend fun getActiveBillCount(): Int = dao.getActiveBillCount()

    suspend fun getPayeesForBill(billId: Long): List<BillPayee> = dao.getPayeesForBill(billId)

    suspend fun replacePayees(billId: Long, payees: List<PayeeDraft>) = database.withTransaction {
        dao.deletePayeesForBill(billId)
        if (payees.isNotEmpty()) {
            dao.insertPayees(payees.map {
                BillPayee(billId = billId, name = it.name, sharePercent = it.sharePercent)
            })
        }
    }

    suspend fun deletePayeesForBill(billId: Long) = dao.deletePayeesForBill(billId)

    /** Runs [block] inside a single database transaction. */
    suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction(block)

    private fun getMonthRange(year: Int, month: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
        }.timeInMillis
        return start to end
    }
}
