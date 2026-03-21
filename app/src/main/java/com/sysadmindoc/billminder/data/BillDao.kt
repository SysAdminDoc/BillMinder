package com.sysadmindoc.billminder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    @Query("SELECT * FROM bills WHERE isEnabled = 1 ORDER BY dueDay ASC")
    fun getAllBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills ORDER BY dueDay ASC")
    fun getAllBillsIncludingDisabled(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE isEnabled = 1 AND (name LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') ORDER BY dueDay ASC")
    fun searchBills(query: String): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE isEnabled = 1 AND category = :category ORDER BY dueDay ASC")
    fun getBillsByCategory(category: BillCategory): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getBillById(id: Long): Bill?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: Bill): Long

    @Update
    suspend fun updateBill(bill: Bill)

    @Delete
    suspend fun deleteBill(bill: Bill)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteBillById(id: Long)

    // Payments
    @Query("SELECT * FROM payments WHERE billId = :billId ORDER BY paidAt DESC")
    fun getPaymentsForBill(billId: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments ORDER BY paidAt DESC")
    fun getAllPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE paidAt >= :startOfMonth AND paidAt < :endOfMonth ORDER BY paidAt DESC")
    fun getPaymentsForMonth(startOfMonth: Long, endOfMonth: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE billId = :billId AND dueDate = :dueDate LIMIT 1")
    suspend fun getPaymentForBillDue(billId: Long, dueDate: Long): Payment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("SELECT * FROM bills WHERE isEnabled = 1")
    suspend fun getAllBillsList(): List<Bill>

    @Query("SELECT * FROM bills")
    suspend fun getAllBillsForExport(): List<Bill>

    @Query("SELECT * FROM payments")
    suspend fun getAllPaymentsForExport(): List<Payment>

    // Lifetime spending per bill
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM payments WHERE billId = :billId")
    suspend fun getLifetimeSpending(billId: Long): Double

    // Total lifetime spending
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM payments")
    suspend fun getTotalLifetimeSpending(): Double

    // Spending by category (via bill join)
    @Query("SELECT b.category, COALESCE(SUM(p.amount), 0.0) as total FROM bills b LEFT JOIN payments p ON b.id = p.billId WHERE p.paidAt >= :startOfMonth AND p.paidAt < :endOfMonth GROUP BY b.category")
    suspend fun getSpendingByCategory(startOfMonth: Long, endOfMonth: Long): List<CategorySpending>

    // Monthly spending totals for trend chart
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM payments WHERE paidAt >= :startOfMonth AND paidAt < :endOfMonth")
    suspend fun getMonthlySpendingTotal(startOfMonth: Long, endOfMonth: Long): Double

    // Count consecutive on-time payments (paid before due date) for streak
    @Query("SELECT * FROM payments WHERE billId = :billId ORDER BY dueDate DESC")
    suspend fun getPaymentHistoryForStreak(billId: Long): List<Payment>

    // Count total bills
    @Query("SELECT COUNT(*) FROM bills WHERE isEnabled = 1")
    suspend fun getActiveBillCount(): Int
}

data class CategorySpending(
    val category: BillCategory,
    val total: Double
)
