package com.sysadmindoc.billminder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    @Query("SELECT * FROM bills WHERE isEnabled = 1 ORDER BY dueDay ASC")
    fun getAllBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills ORDER BY dueDay ASC")
    fun getAllBillsIncludingDisabled(): Flow<List<Bill>>

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
}
