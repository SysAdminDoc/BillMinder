package com.sysadmindoc.billminder.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream

/**
 * A bill's payment history has to survive an import. The anchor is derived from the earliest date
 * in the file, so older rows land on their own occurrence rather than piling onto one cycle key and
 * being dropped by the unique index.
 */
@RunWith(RobolectricTestRunner::class)
class CsvImportRoundTripTest {

    private lateinit var context: Context
    private lateinit var database: BillDatabase
    private lateinit var repo: BillRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BillRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun importing(csv: String): CsvImportResult {
        val uri = Uri.parse("content://test/import.csv")
        shadowOf(context.contentResolver)
            .registerInputStream(uri, ByteArrayInputStream(csv.toByteArray()))
        val table = runBlocking { CsvImport.read(context, uri)!! }
        shadowOf(context.contentResolver)
            .registerInputStream(uri, ByteArrayInputStream(csv.toByteArray()))
        val mapping = CsvImportMapping(CsvImport.suggestedMapping(table.headers))
        return runBlocking { BackupManager.importCsv(context, uri, repo, mapping) }
    }

    @Test
    fun `newest first history keeps every payment`() {
        val result = importing(
            """
            Name,Amount,Due date,Recurrence,Payment date,Payment amount
            Rent,1200,2026-06-01,Monthly,2026-06-01,1200
            Rent,1200,2026-05-01,Monthly,2026-05-01,1200
            Rent,1200,2026-04-01,Monthly,2026-04-01,1200
            """.trimIndent()
        )

        assertEquals(1, result.billsImported)
        assertEquals(3, result.paymentsImported)
        assertEquals(0, result.rowsSkipped)

        val bill = runBlocking { repo.getAllBillsForExport() }.single()
        val keys = runBlocking { repo.getPaidCycleKeys(bill.id) }
        assertEquals(setOf("2026-04-01", "2026-05-01", "2026-06-01"), keys)
    }

    @Test
    fun `history with no due date column anchors on the earliest payment`() {
        // This is the shape of the app's own CSV export, which carries a due day but no due date.
        val result = importing(
            """
            Name,Amount,Due day,Recurrence,Payment date,Payment amount
            Internet,80,15,Monthly,2026-03-15,80
            Internet,80,15,Monthly,2026-02-15,80
            Internet,80,15,Monthly,2026-01-15,80
            """.trimIndent()
        )

        assertEquals(1, result.billsImported)
        assertEquals(3, result.paymentsImported)
        val bill = runBlocking { repo.getAllBillsForExport() }.single()
        assertEquals(
            setOf("2026-01-15", "2026-02-15", "2026-03-15"),
            runBlocking { repo.getPaidCycleKeys(bill.id) }
        )
    }

    @Test
    fun `a repeated row for the same cycle is reported as skipped`() {
        val result = importing(
            """
            Name,Amount,Due date,Recurrence,Payment date,Payment amount
            Gym,40,2026-02-01,Monthly,2026-02-01,40
            Gym,40,2026-02-01,Monthly,2026-02-01,40
            """.trimIndent()
        )
        assertEquals(1, result.paymentsImported)
        assertEquals(1, result.rowsSkipped)
    }
}
