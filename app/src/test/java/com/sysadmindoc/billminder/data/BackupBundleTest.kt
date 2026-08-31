package com.sysadmindoc.billminder.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.GsonBuilder
import com.sysadmindoc.billminder.domain.CycleEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupBundleTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databases = mutableListOf<BillDatabase>()
    private val secret = "correct horse battery staple".toCharArray()

    @Before
    fun setUp() {
        BackupSettingsStore.apply(context, defaultSettings())
    }

    @After
    fun tearDown() {
        secret.fill('\u0000')
        databases.forEach(BillDatabase::close)
    }

    @Test
    fun `encrypted bundle round trips the full graph receipts and supported settings`() = runBlocking {
        val source = repository()
        val attachments = FakeAttachmentAccess().apply {
            live["source.bin"] = "private receipt bytes".toByteArray()
        }
        val bill = source.saveBillWithPayees(
            sampleBill("Rent"),
            listOf(PayeeDraft("Sam", 60.0), PayeeDraft("Alex", 40.0))
        )!!
        source.insertPayment(
            Payment(
                billId = bill.id,
                amount = 1000.0,
                paidAt = 1_767_225_600_000L,
                dueDate = CycleEngine.dueInstant(LocalDate.parse("2026-01-01")),
                cycleKey = "2026-01-01",
                attachmentName = "receipt.pdf",
                attachmentFile = "source.bin",
                attachmentMime = "application/pdf"
            )
        )
        val expectedSettings = RestorableSettings(
            displayCurrency = "EUR",
            manualRates = mapOf("EUR" to 0.91),
            categoryBudgets = mapOf(BillCategory.RENT to CategoryBudget(1500.0, "EUR")),
            fullScreenReminders = true,
            vacationMode = true,
            maskExternalContent = true,
            hideAmountsInApp = true
        )
        BackupSettingsStore.apply(context, expectedSettings)

        val bytes = export(source, attachments)
        assertTrue(bytes.copyOfRange(0, 8).contentEquals("BMBACKUP".toByteArray()))
        val preview = PortableBackupBundle.previewFrom(context, ByteArrayInputStream(bytes), secret)
        assertEquals(1, preview.bills)
        assertEquals(1, preview.payments)
        assertEquals(2, preview.payees)
        assertEquals(1, preview.receipts)
        assertEquals("private receipt bytes".toByteArray().size.toLong(), preview.receiptBytes)

        val target = repository()
        target.insertBill(sampleBill("Existing"))
        BackupSettingsStore.apply(context, defaultSettings())
        val replaced = PortableBackupBundle.restoreFrom(
            context,
            ByteArrayInputStream(bytes),
            target,
            secret,
            BackupRestorePolicy.REPLACE,
            attachments
        )
        assertEquals(BackupRestorePolicy.REPLACE, replaced.policy)
        assertEquals(listOf("Rent"), target.getAllBillsForExport().map(Bill::name))
        assertEquals(2, target.getAllPayeesForExport().size)
        val restoredPayment = target.getAllPaymentsForExport().single()
        assertNotEquals("source.bin", restoredPayment.attachmentFile)
        assertTrue(attachments.live[restoredPayment.attachmentFile]!!.contentEquals("private receipt bytes".toByteArray()))
        assertEquals(expectedSettings, BackupSettingsStore.capture(context))

        PortableBackupBundle.restoreFrom(
            context,
            ByteArrayInputStream(bytes),
            target,
            secret,
            BackupRestorePolicy.MERGE,
            attachments
        )
        assertEquals(2, target.getAllBillsForExport().size)
        assertEquals(2, target.getAllPaymentsForExport().size)
        assertEquals(4, target.getAllPayeesForExport().size)
        assertEquals(2, target.getAllPaymentsForExport().map(Payment::billId).toSet().size)
    }

    @Test
    fun `wrong passphrase tampering and truncation leave live data unchanged`() = runBlocking {
        val source = repository().also { it.insertBill(sampleBill("Imported")) }
        val bytes = export(source, FakeAttachmentAccess())
        val tampered = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte() }
        val truncated = bytes.copyOf(bytes.size - 8)
        val target = repository().also { it.insertBill(sampleBill("Keep me")) }

        val attempts = listOf(
            bytes to "wrong passphrase".toCharArray(),
            tampered to secret,
            truncated to secret
        )
        attempts.forEach { (candidate, passphrase) ->
            val result = runCatching {
                PortableBackupBundle.restoreFrom(
                    context,
                    ByteArrayInputStream(candidate),
                    target,
                    passphrase,
                    BackupRestorePolicy.REPLACE,
                    FakeAttachmentAccess()
                )
            }
            assertTrue(result.isFailure)
            assertEquals(listOf("Keep me"), target.getAllBillsForExport().map(Bill::name))
            if (passphrase !== secret) passphrase.fill('\u0000')
        }
    }

    @Test
    fun `duplicate IDs checksum mismatches and newer schemas are rejected before mutation`() = runBlocking {
        val source = repository().also { it.insertBill(sampleBill("Imported")) }
        val bytes = export(source, FakeAttachmentAccess())
        val duplicate = rewrite(
            bytes,
            payloadTransform = { _, payload ->
                payload.copy(bills = payload.bills.orEmpty() + payload.bills.orEmpty().first())
            }
        )
        val checksumMismatch = rewrite(bytes, archiveTransform = { entries ->
            val data = entries.getValue("data.json").copyOf()
            val marker = String(data, Charsets.UTF_8).indexOf("Imported")
            check(marker >= 0)
            data[marker] = 'i'.code.toByte()
            entries["data.json"] = data
        })
        val future = rewrite(bytes, manifestTransform = { manifest ->
            manifest.copy(schemaVersion = PortableBackupBundle.SCHEMA_VERSION + 1)
        })
        val target = repository().also { it.insertBill(sampleBill("Keep me")) }

        listOf(duplicate, checksumMismatch, future).forEach { candidate ->
            val result = runCatching {
                PortableBackupBundle.restoreFrom(
                    context,
                    ByteArrayInputStream(candidate),
                    target,
                    secret,
                    BackupRestorePolicy.REPLACE,
                    FakeAttachmentAccess()
                )
            }
            assertTrue(result.isFailure)
            assertEquals(listOf("Keep me"), target.getAllBillsForExport().map(Bill::name))
        }
    }

    @Test
    fun `receipt install failure rolls the database transaction back`() = runBlocking {
        val source = repository()
        val attachments = FakeAttachmentAccess().apply {
            live["source-1.bin"] = byteArrayOf(1, 2, 3)
            live["source-2.bin"] = byteArrayOf(4, 5, 6)
        }
        val bill = source.insertBill(sampleBill("Imported"))
        listOf("2026-01-01", "2026-02-01").forEachIndexed { index, cycle ->
            source.insertPayment(
                Payment(
                    billId = bill,
                    amount = 10.0,
                    dueDate = CycleEngine.dueInstant(LocalDate.parse(cycle)),
                    cycleKey = cycle,
                    attachmentFile = "source-${index + 1}.bin"
                )
            )
        }
        val bytes = export(source, attachments)
        val target = repository().also { it.insertBill(sampleBill("Keep me")) }
        val previousSettings = defaultSettings().copy(displayCurrency = "GBP")
        BackupSettingsStore.apply(context, previousSettings)
        attachments.failInstallAt = 2

        val result = runCatching {
            PortableBackupBundle.restoreFrom(
                context,
                ByteArrayInputStream(bytes),
                target,
                secret,
                BackupRestorePolicy.REPLACE,
                attachments
            )
        }
        assertTrue(result.isFailure)
        assertEquals(listOf("Keep me"), target.getAllBillsForExport().map(Bill::name))
        assertEquals(previousSettings, BackupSettingsStore.capture(context))
        assertTrue(attachments.live.keys.none { it.startsWith("restored-") })
    }

    @Test
    fun `old receipt cleanup failure does not undo a committed replacement`() = runBlocking {
        val source = repository()
        val attachments = FakeAttachmentAccess().apply {
            live["source.bin"] = byteArrayOf(1, 2, 3)
            live["old.bin"] = byteArrayOf(9, 9, 9)
            failDeleteFor = "old.bin"
        }
        val sourceBill = source.insertBill(sampleBill("Imported"))
        source.insertPayment(
            Payment(
                billId = sourceBill,
                amount = 10.0,
                dueDate = CycleEngine.dueInstant(LocalDate.parse("2026-01-01")),
                cycleKey = "2026-01-01",
                attachmentFile = "source.bin"
            )
        )
        val bytes = export(source, attachments)
        val target = repository()
        val oldBill = target.insertBill(sampleBill("Old"))
        target.insertPayment(
            Payment(
                billId = oldBill,
                amount = 20.0,
                dueDate = CycleEngine.dueInstant(LocalDate.parse("2025-01-01")),
                cycleKey = "2025-01-01",
                attachmentFile = "old.bin"
            )
        )

        val result = PortableBackupBundle.restoreFrom(
            context,
            ByteArrayInputStream(bytes),
            target,
            secret,
            BackupRestorePolicy.REPLACE,
            attachments
        )

        assertEquals(BackupRestorePolicy.REPLACE, result.policy)
        assertEquals(listOf("Imported"), target.getAllBillsForExport().map(Bill::name))
        val restoredName = target.getAllPaymentsForExport().single().attachmentFile
        assertTrue(attachments.live.getValue(restoredName).contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue("old.bin" in attachments.live)
    }

    @Test
    fun `older JSON backups still import without broken receipt references`() = runBlocking {
        val target = repository().also { it.insertBill(sampleBill("Keep me")) }
        val legacyBill = sampleBill("Older backup").copy(id = 7L)
        val legacyPayment = Payment(
            id = 9L,
            billId = legacyBill.id,
            amount = 1000.0,
            dueDate = CycleEngine.dueInstant(LocalDate.parse("2026-01-01")),
            cycleKey = "2026-01-01",
            attachmentName = "missing.pdf",
            attachmentFile = "device-only.bin",
            attachmentMime = "application/pdf"
        )
        val uri = Uri.parse("content://billminder.test/legacy.json")
        val json = GsonBuilder().create().toJson(
            LegacyBackupData(version = 5, bills = listOf(legacyBill), payments = listOf(legacyPayment))
        )
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(json.toByteArray()))

        val result = BackupManager.importLegacyJson(context, uri, target)
        assertEquals(LegacyBackupImportResult(1, 1), result)
        assertEquals(setOf("Keep me", "Older backup"), target.getAllBillsForExport().map(Bill::name).toSet())
        val restored = target.getAllPaymentsForExport().single()
        assertTrue(restored.attachmentName.isBlank())
        assertTrue(restored.attachmentFile.isBlank())
        assertEquals("Older backup", target.getBillById(restored.billId)!!.name)
    }

    private fun repository(): BillRepository {
        val database = Room.inMemoryDatabaseBuilder(context, BillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        databases += database
        return BillRepository(database)
    }

    private fun sampleBill(name: String) = Bill(
        name = name,
        amount = 1000.0,
        dueDay = 1,
        recurrence = Recurrence.MONTHLY,
        currency = "USD",
        anchorEpochDay = LocalDate.parse("2026-01-01").toEpochDay()
    )

    private suspend fun export(
        source: BillRepository,
        attachments: BackupAttachmentAccess
    ): ByteArray = ByteArrayOutputStream().use { output ->
        PortableBackupBundle.exportTo(
            context,
            output,
            source,
            secret,
            attachments,
            kdfIterations = 100_000
        )
        output.toByteArray()
    }

    private fun defaultSettings() = RestorableSettings(
        displayCurrency = "USD",
        manualRates = emptyMap(),
        categoryBudgets = emptyMap(),
        fullScreenReminders = false,
        vacationMode = false,
        maskExternalContent = false,
        hideAmountsInApp = false
    )

    private fun rewrite(
        source: ByteArray,
        manifestTransform: (PortableBackupManifest) -> PortableBackupManifest = { it },
        payloadTransform: (PortableBackupManifest, PortableBackupPayload) -> PortableBackupPayload =
            { _, payload -> payload },
        archiveTransform: (MutableMap<String, ByteArray>) -> Unit = {}
    ): ByteArray {
        val zipBytes = ByteArrayOutputStream().also {
            BackupContainer.decrypt(ByteArrayInputStream(source), it, secret)
        }.toByteArray()
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        val gson = GsonBuilder().disableHtmlEscaping().create()
        val manifest = gson.fromJson(String(entries.getValue("manifest.json")), PortableBackupManifest::class.java)
        val payload = gson.fromJson(String(entries.getValue("data.json")), PortableBackupPayload::class.java)
        val changedPayload = payloadTransform(manifest, payload)
        val data = gson.toJson(changedPayload).toByteArray()
        val changedEntries = manifest.entries.orEmpty().map { entry ->
            if (entry.path == "data.json") {
                entry.copy(byteCount = data.size.toLong(), sha256 = hash(data))
            } else {
                entry
            }
        }
        val changedManifest = manifestTransform(manifest.copy(entries = changedEntries))
        entries["manifest.json"] = gson.toJson(changedManifest).toByteArray()
        entries["data.json"] = data
        archiveTransform(entries)

        val changedZip = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        return ByteArrayOutputStream().use { output ->
            BackupContainer.encrypt(
                ByteArrayInputStream(changedZip),
                output,
                secret,
                iterations = 100_000
            )
            output.toByteArray()
        }
    }

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private class FakeAttachmentAccess : BackupAttachmentAccess {
        val live = mutableMapOf<String, ByteArray>()
        var failInstallAt: Int? = null
        var failDeleteFor: String? = null
        private var nextId = 0
        private var installCount = 0

        override suspend fun exportPlaintext(context: Context, storedName: String, target: File): Long {
            val bytes = live[storedName] ?: throw IOException("missing fake receipt")
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            return bytes.size.toLong()
        }

        override suspend fun prepareRestore(context: Context, plaintext: File, target: File) {
            target.parentFile?.mkdirs()
            plaintext.copyTo(target)
        }

        override fun newStoredName(): String = "restored-${++nextId}.bin"

        override fun install(context: Context, prepared: File, storedName: String) {
            installCount++
            if (installCount == failInstallAt) throw IOException("simulated receipt install failure")
            live[storedName] = prepared.readBytes()
            prepared.delete()
        }

        override fun delete(context: Context, storedName: String) {
            if (storedName == failDeleteFor) throw IOException("simulated receipt cleanup failure")
            live.remove(storedName)
        }
    }
}
