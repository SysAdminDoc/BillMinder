package com.sysadmindoc.billminder.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class BillRepository(private val dao: BillDao) {

    val allBills: Flow<List<Bill>> = dao.getAllBills()
    val allPayments: Flow<List<Payment>> = dao.getAllPayments()

    fun searchBills(query: String): Flow<List<Bill>> = dao.searchBills(query)

    fun getBillsByCategory(category: BillCategory): Flow<List<Bill>> =
        dao.getBillsByCategory(category)

    fun getPaymentsForBill(billId: Long): Flow<List<Payment>> =
        dao.getPaymentsForBill(billId)

    fun getPaymentsForMonth(year: Int, month: Int): Flow<List<Payment>> {
        val (start, end) = getMonthRange(year, month)
        return dao.getPaymentsForMonth(start, end)
    }

    suspend fun getBillById(id: Long): Bill? = dao.getBillById(id)

    suspend fun insertBill(bill: Bill): Long = dao.insertBill(bill)

    suspend fun updateBill(bill: Bill) = dao.updateBill(bill)

    suspend fun deleteBill(bill: Bill) = dao.deleteBill(bill)

    suspend fun deleteBillById(id: Long) = dao.deleteBillById(id)

    suspend fun insertPayment(payment: Payment): Long = dao.insertPayment(payment)

    suspend fun deletePayment(payment: Payment) = dao.deletePayment(payment)

    suspend fun getPaymentForBillDue(billId: Long, dueDate: Long): Payment? =
        dao.getPaymentForBillDue(billId, dueDate)

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
