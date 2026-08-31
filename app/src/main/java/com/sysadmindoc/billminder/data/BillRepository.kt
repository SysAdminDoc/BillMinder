package com.sysadmindoc.billminder.data

import com.sysadmindoc.billminder.domain.CycleEngine
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

class BillRepository(private val dao: BillDao) {

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

    suspend fun replacePayees(billId: Long, payees: List<PayeeDraft>) {
        dao.deletePayeesForBill(billId)
        if (payees.isNotEmpty()) {
            dao.insertPayees(payees.map {
                BillPayee(billId = billId, name = it.name, sharePercent = it.sharePercent)
            })
        }
    }

    suspend fun deletePayeesForBill(billId: Long) = dao.deletePayeesForBill(billId)

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
