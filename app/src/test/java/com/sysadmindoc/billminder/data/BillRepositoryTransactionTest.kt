package com.sysadmindoc.billminder.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sysadmindoc.billminder.domain.CycleEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BillRepositoryTransactionTest {

    private lateinit var database: BillDatabase
    private lateinit var repo: BillRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BillDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = BillRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleBill(name: String = "Rent") = Bill(
        name = name,
        amount = 1000.0,
        dueDay = 1,
        recurrence = Recurrence.MONTHLY,
        anchorEpochDay = LocalDate.parse("2026-01-01").toEpochDay()
    )

    @Test
    fun `saving a bill and its payees is one unit`() = runBlocking {
        val saved = repo.saveBillWithPayees(
            sampleBill(),
            listOf(PayeeDraft("Sam", 50.0), PayeeDraft("Alex", 50.0))
        )
        assertNotNull(saved)
        assertEquals(2, repo.getPayeesForBill(saved!!.id).size)

        repo.saveBillWithPayees(saved.copy(amount = 1100.0), listOf(PayeeDraft("Sam", 100.0)))
        val payees = repo.getPayeesForBill(saved.id)
        assertEquals(1, payees.size)
        assertEquals("Sam", payees[0].name)
        assertEquals(1100.0, repo.getBillById(saved.id)!!.amount, 0.001)
    }

    @Test
    fun `a failed save leaves the previous payee split untouched`() = runBlocking {
        val saved = repo.saveBillWithPayees(sampleBill(), listOf(PayeeDraft("Sam", 100.0)))!!
        val failed = runCatching {
            repo.inTransaction {
                database.billDao().deletePayeesForBill(saved.id)
                database.billDao().insertPayees(
                    listOf(BillPayee(billId = 9_999L, name = "Ghost", sharePercent = 100.0))
                )
            }
        }
        assertTrue("insert against a missing bill must fail", failed.isFailure)
        val payees = repo.getPayeesForBill(saved.id)
        assertEquals(1, payees.size)
        assertEquals("Sam", payees[0].name)
    }

    @Test
    fun `deleting a bill removes the whole graph and undo restores it exactly`() = runBlocking {
        val saved = repo.saveBillWithPayees(sampleBill(), listOf(PayeeDraft("Sam", 100.0)))!!
        repo.insertPayment(
            Payment(
                billId = saved.id,
                amount = 1000.0,
                dueDate = CycleEngine.dueInstant(LocalDate.parse("2026-01-01")),
                cycleKey = "2026-01-01",
                attachmentFile = "receipt.bin"
            )
        )

        val graph = repo.deleteBillGraph(saved.id)
        assertNotNull(graph)
        assertEquals(1, graph!!.payments.size)
        assertEquals(1, graph.payees.size)
        assertEquals(listOf("receipt.bin"), graph.attachmentFiles)
        assertNull(repo.getBillById(saved.id))
        assertEquals(0, repo.getAllPaymentsForExport().size)
        assertEquals(0, repo.getPayeesForBill(saved.id).size)

        val restored = repo.restoreBillGraph(graph)
        assertNotNull(restored)
        assertEquals("the original identifier comes back", saved.id, restored!!.id)
        assertEquals(1, repo.getPayeesForBill(saved.id).size)
        val payments = repo.getPaymentsForBillList(saved.id)
        assertEquals(1, payments.size)
        assertEquals("2026-01-01", payments[0].cycleKey)
        assertEquals("receipt.bin", payments[0].attachmentFile)
    }

    @Test
    fun `marking the same cycle paid twice records one payment`() = runBlocking {
        val saved = repo.saveBillWithPayees(sampleBill(), emptyList())!!
        val payment = Payment(
            billId = saved.id,
            amount = 1000.0,
            dueDate = CycleEngine.dueInstant(LocalDate.parse("2026-02-01")),
            cycleKey = "2026-02-01"
        )
        assertTrue(repo.insertPayment(payment) > 0L)
        assertEquals(-1L, repo.insertPayment(payment.copy(amount = 5.0)))
        val payments = repo.getPaymentsForBillList(saved.id)
        assertEquals(1, payments.size)
        assertEquals(1000.0, payments[0].amount, 0.001)
    }

    @Test
    fun `a saved bill always carries an anchor`() = runBlocking {
        val bare = Bill(
            name = "Legacy",
            amount = 20.0,
            dueDay = 12,
            recurrence = Recurrence.MONTHLY,
            createdAt = LocalDate.parse("2026-03-01").atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        )
        val saved = repo.saveBillWithPayees(bare, null)!!
        assertEquals(LocalDate.parse("2026-03-12").toEpochDay(), saved.anchorEpochDay)
    }

    @Test
    fun `the current cycle stays on the oldest unpaid occurrence`() = runBlocking {
        val saved = repo.saveBillWithPayees(sampleBill(), null)!!
        val today = LocalDate.parse("2026-04-10")
        assertEquals(LocalDate.parse("2026-01-01"), repo.currentCycleFor(saved, today))

        listOf("2026-01-01", "2026-02-01", "2026-03-01").forEach { key -> pay(saved.id, key) }
        assertEquals(LocalDate.parse("2026-04-01"), repo.currentCycleFor(saved, today))

        // Once everything up to today is settled the cycle holds, so the bill reads as paid
        // instead of silently moving to May and inviting a second payment.
        pay(saved.id, "2026-04-01")
        assertEquals(LocalDate.parse("2026-04-01"), repo.currentCycleFor(saved, today))
    }

    @Test
    fun `marking paid twice in a row cannot charge a second cycle`() = runBlocking {
        val saved = repo.saveBillWithPayees(sampleBill(), null)!!
        val today = LocalDate.parse("2026-04-10")
        listOf("2026-01-01", "2026-02-01", "2026-03-01").forEach { key -> pay(saved.id, key) }

        repeat(2) {
            val cycle = repo.currentCycleFor(saved, today)!!
            repo.insertPayment(
                Payment(
                    billId = saved.id,
                    amount = 1000.0,
                    dueDate = CycleEngine.dueInstant(cycle),
                    cycleKey = CycleEngine.cycleKey(cycle)
                )
            )
        }
        assertEquals(4, repo.getPaymentsForBillList(saved.id).size)
    }

    private suspend fun pay(billId: Long, key: String) {
        repo.insertPayment(
            Payment(
                billId = billId,
                amount = 1000.0,
                dueDate = CycleEngine.dueInstant(LocalDate.parse(key)),
                cycleKey = key
            )
        )
    }
}
