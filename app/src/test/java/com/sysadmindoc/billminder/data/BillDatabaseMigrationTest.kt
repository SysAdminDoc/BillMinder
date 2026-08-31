package com.sysadmindoc.billminder.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
import java.time.ZoneId

/**
 * Builds a real database at each historical schema version, migrates it forward, and checks that
 * nothing the user cared about was lost. The app no longer falls back to wiping the database, so a
 * broken migration has to surface here.
 */
@RunWith(RobolectricTestRunner::class)
class BillDatabaseMigrationTest {

    private val name = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun openRaw(version: Int, onCreate: (SupportSQLiteDatabase) -> Unit): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    private fun openMigrated(): BillDatabase =
        Room.databaseBuilder(context, BillDatabase::class.java, name)
            .addMigrations(*BillDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `version 1 data survives every migration`() {
        openRaw(1) { db ->
            db.execSQL(V1_BILLS)
            db.execSQL(V1_PAYMENTS)
            db.execSQL(
                "INSERT INTO bills (id, name, amount, dueDay, dueMonth, dueYear, category, " +
                    "recurrence, isAutoPay, notes, reminderTiming, secondReminderTiming, " +
                    "isEnabled, createdAt, color) VALUES " +
                    "(1, 'Rent', 1200.0, 1, NULL, NULL, 'RENT', 'MONTHLY', 0, '', 'ONE_DAY', " +
                    "NULL, 1, ${millisOf("2024-01-01")}, 1)"
            )
            db.execSQL(
                "INSERT INTO payments (id, billId, amount, paidAt, dueDate, note) VALUES " +
                    "(1, 1, 1200.0, ${millisOf("2024-02-01")}, ${millisOf("2024-02-01")}, '')"
            )
        }.close()

        val db = openMigrated()
        try {
            val bills = runBlocking { db.billDao().getAllBillsForExport() }
            assertEquals(1, bills.size)
            assertEquals("Rent", bills[0].name)
            assertEquals(1200.0, bills[0].amount, 0.001)
            assertEquals("USD", bills[0].currency)
            assertTrue("anchor should be backfilled", bills[0].anchorEpochDay > 0L)

            val payments = runBlocking { db.billDao().getAllPaymentsForExport() }
            assertEquals(1, payments.size)
            assertEquals("2024-02-01", payments[0].cycleKey)
        } finally {
            db.close()
        }
    }

    @Test
    fun `version 6 duplicates collapse and orphans are dropped`() {
        openRaw(6) { db ->
            db.execSQL(V6_BILLS)
            db.execSQL(V6_PAYMENTS)
            db.execSQL(V6_PAYEES)
            db.execSQL(
                "INSERT INTO bills (id, name, amount, dueDay, dueMonth, dueYear, category, " +
                    "recurrence, isAutoPay, notes, reminderTiming, secondReminderTiming, " +
                    "isEnabled, createdAt, color, paymentUrl, tags, isVariableAmount, " +
                    "amountMin, amountMax, currency) VALUES " +
                    "(7, 'Internet', 80.0, 15, NULL, NULL, 'PHONE', 'MONTHLY', 0, '', 'ONE_DAY', " +
                    "NULL, 1, ${millisOf("2024-01-01")}, 1, '', '', 0, NULL, NULL, 'EUR')"
            )
            // Two payments for the same day whose stored timestamps differ only in milliseconds.
            db.execSQL(insertV6Payment(1, 7, millisOf("2024-03-15") + 123L))
            db.execSQL(insertV6Payment(2, 7, millisOf("2024-03-15") + 987L))
            // A payment whose bill no longer exists.
            db.execSQL(insertV6Payment(3, 99, millisOf("2024-04-15")))
            db.execSQL(
                "INSERT INTO bill_payees (id, billId, name, sharePercent) VALUES (1, 7, 'Sam', 50.0)"
            )
        }.close()

        val db = openMigrated()
        try {
            val payments = runBlocking { db.billDao().getAllPaymentsForExport() }
            assertEquals("duplicate cycle collapsed", 1, payments.size)
            assertEquals(1L, payments[0].id)
            assertEquals("2024-03-15", payments[0].cycleKey)
            assertEquals(7L, payments[0].billId)

            val payees = runBlocking { db.billDao().getPayeesForBill(7L) }
            assertEquals(1, payees.size)
            assertEquals("Sam", payees[0].name)

            val bill = runBlocking { db.billDao().getBillById(7L) }
            assertNotNull(bill)
            assertEquals("EUR", bill!!.currency)
            assertEquals(15, CycleEngine.anchor(bill).dayOfMonth)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the unique cycle index survives the migration`() {
        openRaw(6) { db ->
            db.execSQL(V6_BILLS)
            db.execSQL(V6_PAYMENTS)
            db.execSQL(V6_PAYEES)
            db.execSQL(
                "INSERT INTO bills (id, name, amount, dueDay, dueMonth, dueYear, category, " +
                    "recurrence, isAutoPay, notes, reminderTiming, secondReminderTiming, " +
                    "isEnabled, createdAt, color, paymentUrl, tags, isVariableAmount, " +
                    "amountMin, amountMax, currency) VALUES " +
                    "(3, 'Gym', 40.0, 5, NULL, NULL, 'OTHER', 'MONTHLY', 0, '', 'ONE_DAY', " +
                    "NULL, 1, ${millisOf("2024-01-01")}, 1, '', '', 0, NULL, NULL, 'USD')"
            )
        }.close()

        val db = openMigrated()
        try {
            val dao = db.billDao()
            val payment = Payment(billId = 3L, amount = 40.0, dueDate = 0L, cycleKey = "2026-05-05")
            assertTrue(runBlocking { dao.insertPayment(payment) } > 0L)
            assertEquals(-1L, runBlocking { dao.insertPayment(payment.copy(amount = 99.0)) })
            assertEquals(40.0, runBlocking { dao.getPaymentForCycle(3L, "2026-05-05") }!!.amount, 0.001)
        } finally {
            db.close()
        }
    }

    @Test
    fun `deleting a bill removes its payments`() {
        openRaw(6) { db ->
            db.execSQL(V6_BILLS)
            db.execSQL(V6_PAYMENTS)
            db.execSQL(V6_PAYEES)
            db.execSQL(
                "INSERT INTO bills (id, name, amount, dueDay, dueMonth, dueYear, category, " +
                    "recurrence, isAutoPay, notes, reminderTiming, secondReminderTiming, " +
                    "isEnabled, createdAt, color, paymentUrl, tags, isVariableAmount, " +
                    "amountMin, amountMax, currency) VALUES " +
                    "(4, 'Water', 30.0, 9, NULL, NULL, 'UTILITIES', 'MONTHLY', 0, '', 'ONE_DAY', " +
                    "NULL, 1, ${millisOf("2024-01-01")}, 1, '', '', 0, NULL, NULL, 'USD')"
            )
            db.execSQL(insertV6Payment(11, 4, millisOf("2024-05-09")))
        }.close()

        val db = openMigrated()
        try {
            runBlocking { db.billDao().deleteBillById(4L) }
            assertEquals(0, runBlocking { db.billDao().getAllPaymentsForExport() }.size)
            assertNull(runBlocking { db.billDao().getBillById(4L) })
        } finally {
            db.close()
        }
    }

    @Test
    fun `payments land on the bill's own occurrence grid`() {
        openRaw(6) { db ->
            db.execSQL(V6_BILLS)
            db.execSQL(V6_PAYMENTS)
            db.execSQL(V6_PAYEES)
            // The old scheduler anchored on the current month, so a quarterly bill's stored due
            // dates sit in months the anchored engine never emits.
            db.execSQL(v6Bill(id = 20, name = "Insurance", dueDay = 10, recurrence = "QUARTERLY", created = "2024-01-10"))
            db.execSQL(insertV6Payment(30, 20, millisOf("2025-03-10")))
            // A biweekly payment recorded a week off the anchored fortnight.
            db.execSQL(v6Bill(id = 21, name = "Cleaner", dueDay = 6, recurrence = "BIWEEKLY", created = "2024-01-05"))
            db.execSQL(insertV6Payment(31, 21, millisOf("2024-02-09")))
        }.close()

        val db = openMigrated()
        try {
            val quarterly = runBlocking { db.billDao().getBillById(20L) }!!
            val quarterlyKey = runBlocking { db.billDao().getPaidCycleKeys(20L) }.single()
            assertTrue(
                "$quarterlyKey is not a quarterly occurrence",
                CycleEngine.occurrencesInRange(
                    quarterly,
                    LocalDate.parse("2024-01-01"),
                    LocalDate.parse("2026-01-01")
                ).map(CycleEngine::cycleKey).contains(quarterlyKey)
            )

            val biweekly = runBlocking { db.billDao().getBillById(21L) }!!
            val biweeklyKey = runBlocking { db.billDao().getPaidCycleKeys(21L) }.single()
            assertTrue(
                "$biweeklyKey is not a biweekly occurrence",
                CycleEngine.occurrencesInRange(
                    biweekly,
                    LocalDate.parse("2024-01-01"),
                    LocalDate.parse("2024-06-01")
                ).map(CycleEngine::cycleKey).contains(biweeklyKey)
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `a newer database is reported instead of wiped`() {
        openRaw(99) { db ->
            db.execSQL(V6_BILLS)
            db.execSQL(V6_PAYMENTS)
            db.execSQL(V6_PAYEES)
            db.execSQL(
                "INSERT INTO bills (id, name, amount, dueDay, dueMonth, dueYear, category, " +
                    "recurrence, isAutoPay, notes, reminderTiming, secondReminderTiming, " +
                    "isEnabled, createdAt, color, paymentUrl, tags, isVariableAmount, " +
                    "amountMin, amountMax, currency) VALUES " +
                    "(1, 'Future', 10.0, 1, NULL, NULL, 'OTHER', 'MONTHLY', 0, '', 'ONE_DAY', " +
                    "NULL, 1, ${millisOf("2024-01-01")}, 1, '', '', 0, NULL, NULL, 'USD')"
            )
        }.close()

        val db = openMigrated()
        val failure = runCatching { db.openHelper.readableDatabase.version }
        db.close()
        assertTrue("a downgrade must fail loudly", failure.isFailure)

        // The rows are still there for a matching build to pick up.
        val reopened = openRaw(99) { }
        val count = reopened.query("SELECT COUNT(*) FROM bills").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        reopened.close()
        assertEquals(1, count)
    }

    private fun millisOf(date: String): Long =
        LocalDate.parse(date).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun v6Bill(
        id: Long,
        name: String,
        dueDay: Int,
        recurrence: String,
        created: String,
        currency: String = "USD"
    ): String =
        "INSERT INTO bills (id, name, amount, dueDay, dueMonth, dueYear, category, " +
            "recurrence, isAutoPay, notes, reminderTiming, secondReminderTiming, " +
            "isEnabled, createdAt, color, paymentUrl, tags, isVariableAmount, " +
            "amountMin, amountMax, currency) VALUES " +
            "($id, '$name', 10.0, $dueDay, NULL, NULL, 'OTHER', '$recurrence', 0, '', 'ONE_DAY', " +
            "NULL, 1, ${millisOf(created)}, 1, '', '', 0, NULL, NULL, '$currency')"

    private fun insertV6Payment(id: Long, billId: Long, dueDate: Long): String =
        "INSERT INTO payments (id, billId, amount, paidAt, dueDate, note, confirmationNumber, " +
            "attachmentName, attachmentFile, attachmentMime, currency) VALUES " +
            "($id, $billId, 10.0, $dueDate, $dueDate, '', '', '', '', 'application/octet-stream', 'USD')"

    private companion object {
        const val V1_BILLS =
            "CREATE TABLE IF NOT EXISTS bills (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, amount REAL NOT NULL, dueDay INTEGER NOT NULL, " +
                "dueMonth INTEGER, dueYear INTEGER, category TEXT NOT NULL, recurrence TEXT NOT NULL, " +
                "isAutoPay INTEGER NOT NULL, notes TEXT NOT NULL, reminderTiming TEXT NOT NULL, " +
                "secondReminderTiming TEXT, isEnabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
                "color INTEGER NOT NULL)"

        const val V1_PAYMENTS =
            "CREATE TABLE IF NOT EXISTS payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "billId INTEGER NOT NULL, amount REAL NOT NULL, paidAt INTEGER NOT NULL, " +
                "dueDate INTEGER NOT NULL, note TEXT NOT NULL)"

        const val V6_BILLS =
            "CREATE TABLE IF NOT EXISTS bills (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, amount REAL NOT NULL, dueDay INTEGER NOT NULL, " +
                "dueMonth INTEGER, dueYear INTEGER, category TEXT NOT NULL, recurrence TEXT NOT NULL, " +
                "isAutoPay INTEGER NOT NULL, notes TEXT NOT NULL, reminderTiming TEXT NOT NULL, " +
                "secondReminderTiming TEXT, isEnabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
                "color INTEGER NOT NULL, paymentUrl TEXT NOT NULL DEFAULT '', " +
                "tags TEXT NOT NULL DEFAULT '', isVariableAmount INTEGER NOT NULL DEFAULT 0, " +
                "amountMin REAL, amountMax REAL, currency TEXT NOT NULL DEFAULT 'USD')"

        const val V6_PAYMENTS =
            "CREATE TABLE IF NOT EXISTS payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "billId INTEGER NOT NULL, amount REAL NOT NULL, paidAt INTEGER NOT NULL, " +
                "dueDate INTEGER NOT NULL, note TEXT NOT NULL, " +
                "confirmationNumber TEXT NOT NULL DEFAULT '', " +
                "attachmentName TEXT NOT NULL DEFAULT '', attachmentFile TEXT NOT NULL DEFAULT '', " +
                "attachmentMime TEXT NOT NULL DEFAULT 'application/octet-stream', " +
                "currency TEXT NOT NULL DEFAULT 'USD')"

        const val V6_PAYEES =
            "CREATE TABLE IF NOT EXISTS bill_payees (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "billId INTEGER NOT NULL, name TEXT NOT NULL, sharePercent REAL NOT NULL, " +
                "FOREIGN KEY(billId) REFERENCES bills(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
    }
}
