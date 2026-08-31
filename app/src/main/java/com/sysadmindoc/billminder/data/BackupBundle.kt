package com.sysadmindoc.billminder.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sysadmindoc.billminder.notification.ReminderPrefs
import com.sysadmindoc.billminder.security.EncryptedAttachmentStore
import com.sysadmindoc.billminder.security.SecurityPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class BackupRestorePolicy { MERGE, REPLACE }

data class BackupExportResult(
    val bills: Int,
    val payments: Int,
    val payees: Int,
    val receipts: Int,
    val bytesWritten: Long
)

data class BackupPreview(
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    val bills: Int,
    val payments: Int,
    val payees: Int,
    val receipts: Int,
    val receiptBytes: Long,
    val preferenceValues: Int
)

data class BackupRestoreResult(
    val bills: Int,
    val payments: Int,
    val payees: Int,
    val receipts: Int,
    val policy: BackupRestorePolicy
)

class BackupException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal data class PortableBackupManifest(
    val format: String? = null,
    val schemaVersion: Int? = null,
    val appVersion: String? = null,
    val exportedAt: Long? = null,
    val entries: List<PortableBackupEntry>? = null
)

internal data class PortableBackupEntry(
    val path: String? = null,
    val kind: String? = null,
    val receiptId: String? = null,
    val byteCount: Long? = null,
    val sha256: String? = null
)

internal data class PortableBackupPayload(
    val bills: List<Bill>? = null,
    val payments: List<PortableBackupPayment>? = null,
    val payees: List<BillPayee>? = null,
    val settings: PortableBackupSettings? = null
)

internal data class PortableBackupPayment(
    val payment: Payment? = null,
    val receiptId: String? = null
)

internal data class PortableBackupSettings(
    val displayCurrency: String? = null,
    val manualRates: Map<String, Double>? = null,
    val categoryBudgets: Map<String, PortableCategoryBudget>? = null,
    val fullScreenReminders: Boolean? = null,
    val vacationMode: Boolean? = null,
    val maskExternalContent: Boolean? = null,
    val hideAmountsInApp: Boolean? = null
)

internal data class PortableCategoryBudget(
    val amount: Double? = null,
    val currency: String? = null
)

internal data class RestorableSettings(
    val displayCurrency: String,
    val manualRates: Map<String, Double>,
    val categoryBudgets: Map<BillCategory, CategoryBudget>,
    val fullScreenReminders: Boolean,
    val vacationMode: Boolean,
    val maskExternalContent: Boolean,
    val hideAmountsInApp: Boolean
) {
    val valueCount: Int
        get() = 5 + manualRates.size + categoryBudgets.size

    fun toPortable() = PortableBackupSettings(
        displayCurrency = displayCurrency,
        manualRates = manualRates,
        categoryBudgets = categoryBudgets.mapKeys { it.key.name }.mapValues {
            PortableCategoryBudget(it.value.amount, it.value.currency)
        },
        fullScreenReminders = fullScreenReminders,
        vacationMode = vacationMode,
        maskExternalContent = maskExternalContent,
        hideAmountsInApp = hideAmountsInApp
    )
}

internal interface BackupAttachmentAccess {
    suspend fun exportPlaintext(context: Context, storedName: String, target: File): Long
    suspend fun prepareRestore(context: Context, plaintext: File, target: File)
    fun newStoredName(): String
    fun install(context: Context, prepared: File, storedName: String)
    fun delete(context: Context, storedName: String)
}

internal object DeviceBackupAttachmentAccess : BackupAttachmentAccess {
    override suspend fun exportPlaintext(context: Context, storedName: String, target: File): Long =
        EncryptedAttachmentStore.exportPlaintextTo(context, storedName, target)

    override suspend fun prepareRestore(context: Context, plaintext: File, target: File) =
        EncryptedAttachmentStore.prepareRestoreFile(context, plaintext, target)

    override fun newStoredName(): String = EncryptedAttachmentStore.newStoredName()

    override fun install(context: Context, prepared: File, storedName: String) =
        EncryptedAttachmentStore.installPrepared(context, prepared, storedName)

    override fun delete(context: Context, storedName: String) =
        EncryptedAttachmentStore.delete(context, storedName)
}

internal object PortableBackupBundle {
    const val SCHEMA_VERSION = 1
    const val PRODUCTION_KDF_ITERATIONS = 600_000
    private const val FORMAT = "com.sysadmindoc.billminder.portable-backup"
    private const val MANIFEST_PATH = "manifest.json"
    private const val DATA_PATH = "data.json"
    private const val RECEIPT_KIND = "receipt"
    private const val DATA_KIND = "data"
    private const val MAX_MANIFEST_BYTES = 1024L * 1024L
    private const val MAX_DATA_BYTES = 20L * 1024L * 1024L
    private const val MAX_RECEIPT_BYTES = 10L * 1024L * 1024L
    private const val MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L
    private const val MAX_ROWS = 100_000
    private const val MAX_ENTRIES = MAX_ROWS + 1
    private const val CACHE_DIRECTORY = "portable-backup-temp"
    private const val RESTORE_DIRECTORY = "portable-backup-restore"
    private val receiptIdPattern =
        "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"
    private val receiptPath = Regex("receipts/$receiptIdPattern\\.bin")
    private val receiptId = Regex(receiptIdPattern)
    private val sha256Text = Regex("[a-f0-9]{64}")
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    suspend fun exportTo(
        context: Context,
        output: OutputStream,
        repository: BillRepository,
        passphrase: CharArray,
        attachments: BackupAttachmentAccess = DeviceBackupAttachmentAccess,
        kdfIterations: Int = PRODUCTION_KDF_ITERATIONS
    ): BackupExportResult = withContext(Dispatchers.IO) {
        requirePassphrase(passphrase)
        val root = temporaryDirectory(File(context.cacheDir, CACHE_DIRECTORY), "export")
        try {
            val snapshot = repository.snapshotForBackup()
            requireBackup(snapshot.bills.size <= MAX_ROWS, "Backup contains too many bills")
            requireBackup(snapshot.payments.size <= MAX_ROWS, "Backup contains too many payments")
            requireBackup(snapshot.payees.size <= MAX_ROWS, "Backup contains too many payees")

            val receiptEntries = mutableListOf<PortableBackupEntry>()
            var receiptBytes = 0L
            val portablePayments = snapshot.payments.sortedBy { it.id }.map { payment ->
                if (payment.attachmentFile.isBlank()) {
                    PortableBackupPayment(payment.copy(attachmentFile = ""), null)
                } else {
                    val id = UUID.randomUUID().toString().lowercase(Locale.ROOT)
                    val path = "receipts/$id.bin"
                    val file = File(root, path).also { it.parentFile?.mkdirs() }
                    val byteCount = attachments.exportPlaintext(context, payment.attachmentFile, file)
                    requireBackup(byteCount in 0..MAX_RECEIPT_BYTES, "Receipt exceeds the 10 MB limit")
                    receiptBytes += byteCount
                    requireBackup(receiptBytes <= MAX_ARCHIVE_BYTES, "Backup exceeds the 512 MB limit")
                    receiptEntries += PortableBackupEntry(
                        path = path,
                        kind = RECEIPT_KIND,
                        receiptId = id,
                        byteCount = byteCount,
                        sha256 = sha256(file)
                    )
                    PortableBackupPayment(payment.copy(attachmentFile = ""), id)
                }
            }
            requireBackup(
                receiptEntries.size + 1 <= MAX_ENTRIES,
                "Backup contains too many receipt entries"
            )

            val settings = BackupSettingsStore.capture(context)
            val payload = PortableBackupPayload(
                bills = snapshot.bills.sortedBy { it.id },
                payments = portablePayments,
                payees = snapshot.payees.sortedBy { it.id },
                settings = settings.toPortable()
            )
            val dataFile = File(root, DATA_PATH)
            dataFile.writer(Charsets.UTF_8).use { gson.toJson(payload, it) }
            requireBackup(dataFile.length() <= MAX_DATA_BYTES, "Backup data exceeds the 20 MB limit")
            val dataEntry = PortableBackupEntry(
                path = DATA_PATH,
                kind = DATA_KIND,
                byteCount = dataFile.length(),
                sha256 = sha256(dataFile)
            )
            val manifest = PortableBackupManifest(
                format = FORMAT,
                schemaVersion = SCHEMA_VERSION,
                appVersion = appVersion(context),
                exportedAt = System.currentTimeMillis(),
                entries = listOf(dataEntry) + receiptEntries.sortedBy { it.path }
            )
            File(root, MANIFEST_PATH).writer(Charsets.UTF_8).use { gson.toJson(manifest, it) }

            val zipFile = File(root, "payload.zip")
            writeZip(root, zipFile, manifest.entries.orEmpty())
            requireBackup(zipFile.length() <= MAX_ARCHIVE_BYTES, "Backup exceeds the 512 MB limit")
            val bundleFile = File(root, "bundle.bmbak")
            FileInputStream(zipFile).use { plaintext ->
                FileOutputStream(bundleFile).use { encrypted ->
                    BackupContainer.encrypt(plaintext, encrypted, passphrase, kdfIterations)
                }
            }
            FileInputStream(bundleFile).use { it.copyTo(output) }
            output.flush()
            BackupExportResult(
                bills = snapshot.bills.size,
                payments = snapshot.payments.size,
                payees = snapshot.payees.size,
                receipts = receiptEntries.size,
                bytesWritten = bundleFile.length()
            )
        } finally {
            root.deleteRecursively()
        }
    }

    suspend fun previewFrom(
        context: Context,
        input: InputStream,
        passphrase: CharArray
    ): BackupPreview = withContext(Dispatchers.IO) {
        val prepared = readAndValidate(context, input, passphrase)
        try {
            prepared.preview
        } finally {
            prepared.close()
        }
    }

    suspend fun restoreFrom(
        context: Context,
        input: InputStream,
        repository: BillRepository,
        passphrase: CharArray,
        policy: BackupRestorePolicy,
        attachments: BackupAttachmentAccess = DeviceBackupAttachmentAccess
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        val prepared = readAndValidate(context, input, passphrase)
        val restoreRoot = temporaryDirectory(File(context.noBackupFilesDir, RESTORE_DIRECTORY), "restore")
        val previousSettings = BackupSettingsStore.capture(context)
        val previousAttachments = repository.getAllPaymentsForExport()
            .map(Payment::attachmentFile)
            .filter(String::isNotBlank)
            .toSet()
        val plannedNames = mutableSetOf<String>()
        var settingsTouched = false
        try {
            val attachmentNames = mutableMapOf<Long, String>()
            val preparedFiles = mutableListOf<Triple<Long, String, File>>()
            prepared.payments.forEach { portable ->
                val receipt = portable.receiptId?.let(prepared.receiptsById::get) ?: return@forEach
                val storedName = generateSequence(attachments::newStoredName)
                    .first { plannedNames.add(it) }
                val encrypted = File(restoreRoot, storedName)
                attachments.prepareRestore(context, receipt.file, encrypted)
                attachmentNames[portable.payment.id] = storedName
                preparedFiles += Triple(portable.payment.id, storedName, encrypted)
            }

            val result = repository.restoreBackupGraph(
                bills = prepared.bills,
                payments = prepared.payments.map(ValidatedPayment::payment),
                payees = prepared.payees,
                policy = policy,
                attachmentNamesByPaymentId = attachmentNames
            ) {
                preparedFiles.forEach { (_, storedName, encrypted) ->
                    attachments.install(context, encrypted, storedName)
                }
                settingsTouched = true
                BackupSettingsStore.apply(context, prepared.settings)
            }

            if (policy == BackupRestorePolicy.REPLACE) runCatching {
                val currentAttachments = repository.getAllPaymentsForExport()
                    .map(Payment::attachmentFile)
                    .filter(String::isNotBlank)
                    .toSet()
                (previousAttachments - currentAttachments).forEach {
                    runCatching { attachments.delete(context, it) }
                }
            }
            result
        } catch (error: Exception) {
            plannedNames.forEach { runCatching { attachments.delete(context, it) } }
            if (settingsTouched) {
                runCatching { BackupSettingsStore.apply(context, previousSettings) }
                    .exceptionOrNull()
                    ?.let(error::addSuppressed)
            }
            throw if (error is BackupException) error else BackupException(
                error.message ?: "Backup restore failed",
                error
            )
        } finally {
            restoreRoot.deleteRecursively()
            prepared.close()
        }
    }

    private fun readAndValidate(
        context: Context,
        input: InputStream,
        passphrase: CharArray
    ): PreparedBackup {
        requirePassphrase(passphrase)
        val root = temporaryDirectory(File(context.cacheDir, CACHE_DIRECTORY), "preview")
        try {
            val zipFile = File(root, "payload.zip")
            FileOutputStream(zipFile).use { BackupContainer.decrypt(input, it, passphrase) }
            val extracted = extractZip(zipFile, File(root, "contents"))
            val manifestFile = extracted[MANIFEST_PATH]?.file
                ?: throw BackupException("Backup manifest is missing")
            requireBackup(manifestFile.length() <= MAX_MANIFEST_BYTES, "Backup manifest is too large")
            val manifest = parseJson(manifestFile, PortableBackupManifest::class.java, "manifest")
            requireBackup(manifest.format == FORMAT, "This is not a BillMinder portable backup")
            val schema = manifest.schemaVersion ?: throw BackupException("Backup schema is missing")
            requireBackup(
                schema == SCHEMA_VERSION,
                "This backup uses schema $schema; this app supports schema $SCHEMA_VERSION"
            )
            val appVersion = manifest.appVersion?.takeIf(String::isNotBlank)
                ?: throw BackupException("Backup app version is missing")
            val exportedAt = manifest.exportedAt?.takeIf { it > 0L }
                ?: throw BackupException("Backup date is invalid")
            val entries = validateEntries(manifest.entries, extracted)
            val dataFile = entries.single { it.metadata.kind == DATA_KIND }.file
            val payload = parseJson(dataFile, PortableBackupPayload::class.java, "data")
            return validatePayload(root, schema, appVersion, exportedAt, payload, entries)
        } catch (error: Exception) {
            root.deleteRecursively()
            throw if (error is BackupException) error else BackupException(
                error.message ?: "Backup could not be read",
                error
            )
        }
    }

    private fun validateEntries(
        wireEntries: List<PortableBackupEntry>?,
        extracted: Map<String, ExtractedEntry>
    ): List<ValidatedEntry> {
        val entries = wireEntries ?: throw BackupException("Backup entry list is missing")
        requireBackup(entries.size <= MAX_ENTRIES, "Backup contains too many entries")
        val paths = mutableSetOf<String>()
        val receiptIds = mutableSetOf<String>()
        val validated = entries.map { metadata ->
            val path = metadata.path ?: throw BackupException("Backup entry path is missing")
            requireBackup(paths.add(path), "Backup contains duplicate entry paths")
            val kind = metadata.kind ?: throw BackupException("Backup entry kind is missing")
            when (kind) {
                DATA_KIND -> {
                    requireBackup(path == DATA_PATH, "Backup data path is invalid")
                    requireBackup(metadata.receiptId == null, "Data entry has a receipt identifier")
                }
                RECEIPT_KIND -> {
                    val id = metadata.receiptId
                        ?: throw BackupException("Receipt identifier is missing")
                    requireBackup(receiptId.matches(id), "Receipt identifier is invalid")
                    requireBackup(receiptIds.add(id), "Backup contains duplicate receipt identifiers")
                    requireBackup(path == "receipts/$id.bin", "Receipt path is invalid")
                }
                else -> throw BackupException("Backup entry kind is unsupported")
            }
            val actual = extracted[path] ?: throw BackupException("Backup entry $path is missing")
            val expectedBytes = metadata.byteCount ?: throw BackupException("Backup size is missing")
            val expectedHash = metadata.sha256 ?: throw BackupException("Backup checksum is missing")
            requireBackup(expectedBytes == actual.byteCount, "Backup size check failed for $path")
            requireBackup(sha256Text.matches(expectedHash), "Backup checksum is invalid for $path")
            requireBackup(expectedHash == actual.sha256, "Backup checksum failed for $path")
            ValidatedEntry(metadata, actual.file)
        }
        requireBackup(validated.count { it.metadata.kind == DATA_KIND } == 1, "Backup data entry is missing")
        requireBackup(
            extracted.keys - MANIFEST_PATH == paths,
            "Backup contains an unlisted or missing entry"
        )
        return validated
    }

    private fun validatePayload(
        root: File,
        schema: Int,
        appVersion: String,
        exportedAt: Long,
        payload: PortableBackupPayload,
        entries: List<ValidatedEntry>
    ): PreparedBackup {
        return try {
            val bills = payload.bills ?: throw BackupException("Backup bills are missing")
            val portablePayments = payload.payments ?: throw BackupException("Backup payments are missing")
            val payees = payload.payees ?: throw BackupException("Backup payees are missing")
            requireBackup(bills.size <= MAX_ROWS, "Backup contains too many bills")
            requireBackup(portablePayments.size <= MAX_ROWS, "Backup contains too many payments")
            requireBackup(payees.size <= MAX_ROWS, "Backup contains too many payees")

            val billIds = uniquePositiveIds(bills.map(Bill::id), "bill")
            bills.forEach(::validateBill)
            val payeeIds = uniquePositiveIds(payees.map(BillPayee::id), "payee")
            requireBackup(payeeIds.size == payees.size, "Backup contains invalid payee IDs")
            payees.forEach { payee ->
                requireBackup(payee.billId in billIds, "Payee references a missing bill")
                requireBackup(payee.name.isNotBlank() && payee.name.length <= 200, "Payee name is invalid")
                requireBackup(
                    payee.sharePercent.isFinite() && payee.sharePercent > 0.0 && payee.sharePercent <= 100.0,
                    "Payee percentage is invalid"
                )
            }

            val receiptEntries = entries.filter { it.metadata.kind == RECEIPT_KIND }
                .associateBy { it.metadata.receiptId!! }
            val claimedReceipts = mutableSetOf<String>()
            val paymentIds = uniquePositiveIds(
                portablePayments.map { it.payment?.id ?: -1L },
                "payment"
            )
            val cycleKeys = mutableSetOf<Pair<Long, String>>()
            val payments = portablePayments.map { wire ->
                val payment = wire.payment ?: throw BackupException("Backup payment is missing")
                requireBackup(payment.billId in billIds, "Payment references a missing bill")
                validatePayment(payment)
                requireBackup(
                    cycleKeys.add(payment.billId to payment.cycleKey),
                    "Backup contains duplicate bill cycles"
                )
                requireBackup(payment.attachmentFile.isBlank(), "Backup contains a device receipt path")
                wire.receiptId?.let { id ->
                    requireBackup(receiptId.matches(id), "Payment receipt identifier is invalid")
                    requireBackup(id in receiptEntries, "Payment receipt is missing")
                    requireBackup(claimedReceipts.add(id), "Receipt is linked to multiple payments")
                }
                ValidatedPayment(payment, wire.receiptId)
            }
            requireBackup(paymentIds.size == payments.size, "Backup contains invalid payment IDs")
            requireBackup(claimedReceipts == receiptEntries.keys, "Backup contains an unreferenced receipt")

            val settings = BackupSettingsStore.validate(payload.settings)
            val receipts = receiptEntries.mapValues { (_, entry) ->
                ExtractedEntry(
                    file = entry.file,
                    byteCount = entry.metadata.byteCount!!,
                    sha256 = entry.metadata.sha256!!
                )
            }
            PreparedBackup(
                root = root,
                bills = bills,
                payments = payments,
                payees = payees,
                settings = settings,
                receiptsById = receipts,
                preview = BackupPreview(
                    schemaVersion = schema,
                    appVersion = appVersion,
                    exportedAt = exportedAt,
                    bills = bills.size,
                    payments = payments.size,
                    payees = payees.size,
                    receipts = receipts.size,
                    receiptBytes = receipts.values.sumOf(ExtractedEntry::byteCount),
                    preferenceValues = settings.valueCount
                )
            )
        } catch (error: BackupException) {
            throw error
        } catch (error: Exception) {
            throw BackupException("Backup data is invalid", error)
        }
    }

    private fun validateBill(bill: Bill) {
        requireBackup(bill.name.isNotBlank() && bill.name.length <= 300, "Bill name is invalid")
        requireBackup(bill.amount.isFinite() && bill.amount > 0.0, "Bill amount is invalid")
        requireBackup(bill.category in BillCategory.entries, "Bill category is invalid")
        requireBackup(bill.recurrence in Recurrence.entries, "Bill recurrence is invalid")
        requireBackup(bill.reminderTiming in ReminderTiming.entries, "Bill reminder timing is invalid")
        requireBackup(
            bill.secondReminderTiming == null || bill.secondReminderTiming in ReminderTiming.entries,
            "Bill second reminder timing is invalid"
        )
        requireBackup(bill.notes.length <= 20_000 && bill.tags.length <= 5_000, "Bill text is too long")
        requireBackup(bill.paymentUrl.length <= 2_000, "Payment URL is too long")
        val dueRange = if (bill.recurrence == Recurrence.WEEKLY || bill.recurrence == Recurrence.BIWEEKLY) {
            1..7
        } else {
            1..31
        }
        requireBackup(bill.dueDay in dueRange, "Bill due day is invalid")
        bill.dueMonth?.let { requireBackup(it in 0..11, "Bill due month is invalid") }
        bill.dueYear?.let { requireBackup(it in 1900..9999, "Bill due year is invalid") }
        requireBackup(isKnownCurrency(bill.currency), "Bill currency is invalid")
        bill.amountMin?.let { requireBackup(it.isFinite() && it > 0.0, "Bill minimum is invalid") }
        bill.amountMax?.let { requireBackup(it.isFinite() && it > 0.0, "Bill maximum is invalid") }
        if (bill.amountMin != null && bill.amountMax != null) {
            requireBackup(bill.amountMin <= bill.amountMax, "Bill amount range is invalid")
        }
    }

    private fun validatePayment(payment: Payment) {
        requireBackup(payment.amount.isFinite() && payment.amount > 0.0, "Payment amount is invalid")
        requireBackup(payment.cycleKey.isNotBlank(), "Payment cycle is missing")
        runCatching { LocalDate.parse(payment.cycleKey) }
            .getOrElse { throw BackupException("Payment cycle is invalid") }
        requireBackup(isKnownCurrency(payment.currency), "Payment currency is invalid")
        requireBackup(payment.note.length <= 20_000, "Payment note is too long")
        requireBackup(payment.confirmationNumber.length <= 500, "Payment confirmation is too long")
        requireBackup(payment.attachmentName.length <= 500, "Receipt name is too long")
        requireBackup(payment.attachmentMime.length <= 200, "Receipt type is too long")
    }

    private fun uniquePositiveIds(ids: List<Long>, label: String): Set<Long> {
        requireBackup(ids.all { it > 0L }, "Backup contains an invalid $label ID")
        val unique = ids.toSet()
        requireBackup(unique.size == ids.size, "Backup contains duplicate $label IDs")
        return unique
    }

    private fun writeZip(root: File, zipFile: File, entries: List<PortableBackupEntry>) {
        ZipOutputStream(FileOutputStream(zipFile).buffered()).use { zip ->
            writeZipEntry(zip, MANIFEST_PATH, File(root, MANIFEST_PATH))
            entries.forEach { entry ->
                val path = entry.path ?: throw BackupException("Backup entry path is missing")
                writeZipEntry(zip, path, File(root, path))
            }
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, path: String, source: File) {
        requireBackup(source.isFile, "Backup source $path is missing")
        zip.putNextEntry(ZipEntry(path).apply { time = 0L })
        FileInputStream(source).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun extractZip(zipFile: File, destination: File): Map<String, ExtractedEntry> {
        destination.mkdirs()
        val extracted = linkedMapOf<String, ExtractedEntry>()
        var totalBytes = 0L
        ZipInputStream(FileInputStream(zipFile).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                requireBackup(!entry.isDirectory, "Backup contains a directory entry")
                val name = entry.name
                requireBackup(
                    name == MANIFEST_PATH || name == DATA_PATH || receiptPath.matches(name),
                    "Backup entry path is invalid"
                )
                requireBackup(name !in extracted, "Backup contains duplicate ZIP entries")
                requireBackup(extracted.size < MAX_ENTRIES + 1, "Backup contains too many ZIP entries")
                val target = File(destination, name).canonicalFile
                requireBackup(
                    target.path.startsWith(destination.canonicalPath + File.separator),
                    "Backup entry escapes its container"
                )
                target.parentFile?.mkdirs()
                val limit = when (name) {
                    MANIFEST_PATH -> MAX_MANIFEST_BYTES
                    DATA_PATH -> MAX_DATA_BYTES
                    else -> MAX_RECEIPT_BYTES
                }
                val digest = MessageDigest.getInstance("SHA-256")
                val count = FileOutputStream(target).use { copyBounded(zip, it, limit, digest) }
                totalBytes += count
                requireBackup(totalBytes <= MAX_ARCHIVE_BYTES, "Backup expands past the 512 MB limit")
                extracted[name] = ExtractedEntry(target, count, digest.digest().toHex())
                zip.closeEntry()
            }
        }
        return extracted
    }

    private fun <T> parseJson(file: File, type: Class<T>, label: String): T = try {
        file.reader(Charsets.UTF_8).use { gson.fromJson(it, type) }
            ?: throw BackupException("Backup $label is empty")
    } catch (error: BackupException) {
        throw error
    } catch (error: Exception) {
        throw BackupException("Backup $label is invalid", error)
    }

    private fun temporaryDirectory(parent: File, prefix: String): File =
        File(parent, "$prefix-${UUID.randomUUID()}").also {
            check(it.mkdirs()) { "Unable to create backup workspace" }
        }

    fun clearTemporaryFiles(context: Context) {
        File(context.cacheDir, CACHE_DIRECTORY).deleteRecursively()
        File(context.noBackupFilesDir, RESTORE_DIRECTORY).deleteRecursively()
    }

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "unknown" }

    private fun isKnownCurrency(currency: String): Boolean {
        val normalized = currency.uppercase(Locale.ROOT)
        return currency == normalized && CurrencyCatalog.find(normalized).code == normalized
    }

    private data class ExtractedEntry(val file: File, val byteCount: Long, val sha256: String)
    private data class ValidatedEntry(val metadata: PortableBackupEntry, val file: File)
    private data class ValidatedPayment(val payment: Payment, val receiptId: String?)
    private data class PreparedBackup(
        val root: File,
        val bills: List<Bill>,
        val payments: List<ValidatedPayment>,
        val payees: List<BillPayee>,
        val settings: RestorableSettings,
        val receiptsById: Map<String, ExtractedEntry>,
        val preview: BackupPreview
    ) {
        fun close() {
            root.deleteRecursively()
        }
    }
}

internal object BackupSettingsStore {
    fun capture(context: Context) = RestorableSettings(
        displayCurrency = CurrencyPrefs.getDisplayCurrency(context),
        manualRates = CurrencyPrefs.getManualRates(context),
        categoryBudgets = BudgetPrefs.getAll(context),
        fullScreenReminders = ReminderPrefs.isFullScreenEnabled(context),
        vacationMode = ReminderPrefs.isVacationMode(context),
        maskExternalContent = SecurityPrefs.maskExternalContent(context),
        hideAmountsInApp = SecurityPrefs.hideAmountsInApp(context)
    )

    fun validate(wire: PortableBackupSettings?): RestorableSettings {
        val settings = wire ?: throw BackupException("Backup settings are missing")
        val display = settings.displayCurrency ?: throw BackupException("Display currency is missing")
        requireBackup(isKnownCurrency(display), "Display currency is invalid")
        val rates = settings.manualRates ?: throw BackupException("Exchange rates are missing")
        rates.forEach { (currency, rate) ->
            requireBackup(
                currency != "USD" && isKnownCurrency(currency) && rate.isFinite() && rate > 0.0,
                "Manual exchange rate is invalid"
            )
        }
        val budgets = (settings.categoryBudgets ?: throw BackupException("Category budgets are missing"))
            .map { (name, wireBudget) ->
                val category = runCatching { BillCategory.valueOf(name) }
                    .getOrElse { throw BackupException("Budget category is invalid") }
                val amount = wireBudget.amount ?: throw BackupException("Budget amount is missing")
                val currency = wireBudget.currency ?: throw BackupException("Budget currency is missing")
                requireBackup(amount.isFinite() && amount > 0.0, "Budget amount is invalid")
                requireBackup(isKnownCurrency(currency), "Budget currency is invalid")
                category to CategoryBudget(amount, currency)
            }.toMap()
        return RestorableSettings(
            displayCurrency = display,
            manualRates = rates.toSortedMap(),
            categoryBudgets = budgets,
            fullScreenReminders = settings.fullScreenReminders
                ?: throw BackupException("Full-screen reminder setting is missing"),
            vacationMode = settings.vacationMode
                ?: throw BackupException("Vacation setting is missing"),
            maskExternalContent = settings.maskExternalContent
                ?: throw BackupException("External privacy setting is missing"),
            hideAmountsInApp = settings.hideAmountsInApp
                ?: throw BackupException("In-app privacy setting is missing")
        )
    }

    fun apply(context: Context, settings: RestorableSettings) {
        val saved = CurrencyPrefs.restoreFromBackup(
            context,
            settings.displayCurrency,
            settings.manualRates
        ) && BudgetPrefs.restoreFromBackup(
            context,
            settings.categoryBudgets
        ) && ReminderPrefs.restoreFromBackup(
            context,
            settings.fullScreenReminders,
            settings.vacationMode
        ) && SecurityPrefs.restorePrivacyFromBackup(
            context,
            settings.maskExternalContent,
            settings.hideAmountsInApp
        )
        if (!saved) throw BackupException("Backup settings could not be committed")
    }

    private fun isKnownCurrency(currency: String): Boolean {
        val normalized = currency.uppercase(Locale.ROOT)
        return currency == normalized && CurrencyCatalog.find(normalized).code == normalized
    }
}

internal object BackupContainer {
    private val magic = "BMBACKUP".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val MIN_KDF_ITERATIONS = 100_000
    private const val MAX_KDF_ITERATIONS = 2_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val MAX_CONTAINER_BYTES = 512L * 1024L * 1024L
    private const val MAX_CIPHERTEXT_BYTES = MAX_CONTAINER_BYTES + TAG_BITS / Byte.SIZE_BITS

    fun encrypt(
        input: InputStream,
        output: OutputStream,
        passphrase: CharArray,
        iterations: Int = PortableBackupBundle.PRODUCTION_KDF_ITERATIONS
    ) {
        requirePassphrase(passphrase)
        requireBackup(iterations in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS, "Backup KDF is invalid")
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val header = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { data ->
                data.write(magic)
                data.writeInt(VERSION)
                data.writeInt(iterations)
                data.writeInt(salt.size)
                data.write(salt)
                data.writeInt(iv.size)
                data.write(iv)
            }
        }.toByteArray()
        val cipher = cipher(Cipher.ENCRYPT_MODE, passphrase, iterations, salt, iv, header)
        output.write(header)
        crypt(input, output, cipher, MAX_CONTAINER_BYTES)
        output.flush()
    }

    fun decrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        requirePassphrase(passphrase)
        val data = DataInputStream(input)
        val recorded = ByteArrayOutputStream()
        val headerWriter = DataOutputStream(recorded)
        val readMagic = ByteArray(magic.size).also { readFully(data, it) }
        requireBackup(readMagic.contentEquals(magic), "This is not a BillMinder portable backup")
        headerWriter.write(readMagic)
        val version = readInt(data, headerWriter)
        requireBackup(version == VERSION, "This backup container version is not supported")
        val iterations = readInt(data, headerWriter)
        requireBackup(iterations in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS, "Backup KDF is invalid")
        val saltLength = readInt(data, headerWriter)
        requireBackup(saltLength in 16..64, "Backup salt is invalid")
        val salt = ByteArray(saltLength).also { readFully(data, it) }
        headerWriter.write(salt)
        val ivLength = readInt(data, headerWriter)
        requireBackup(ivLength == IV_BYTES, "Backup nonce is invalid")
        val iv = ByteArray(ivLength).also { readFully(data, it) }
        headerWriter.write(iv)
        headerWriter.flush()
        val header = recorded.toByteArray()
        val cipher = cipher(Cipher.DECRYPT_MODE, passphrase, iterations, salt, iv, header)
        try {
            crypt(data, output, cipher, MAX_CIPHERTEXT_BYTES)
            output.flush()
        } catch (error: AEADBadTagException) {
            throw BackupException("Incorrect passphrase or damaged backup", error)
        } catch (error: javax.crypto.BadPaddingException) {
            throw BackupException("Incorrect passphrase or damaged backup", error)
        }
    }

    private fun cipher(
        mode: Int,
        passphrase: CharArray,
        iterations: Int,
        salt: ByteArray,
        iv: ByteArray,
        header: ByteArray
    ): Cipher {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(header)
            }
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun crypt(input: InputStream, output: OutputStream, cipher: Cipher, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var readBytes = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            readBytes += count
            requireBackup(readBytes <= limit, "Backup exceeds the 512 MB limit")
            cipher.update(buffer, 0, count)?.takeIf { it.isNotEmpty() }?.let(output::write)
        }
        cipher.doFinal()?.takeIf { it.isNotEmpty() }?.let(output::write)
    }

    private fun readInt(input: DataInputStream, recorded: DataOutputStream): Int = try {
        input.readInt().also(recorded::writeInt)
    } catch (error: Exception) {
        throw BackupException("Backup header is truncated", error)
    }

    private fun readFully(input: InputStream, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count < 0) throw BackupException("Backup header is truncated")
            offset += count
        }
    }
}

private fun requirePassphrase(passphrase: CharArray) {
    requireBackup(passphrase.size in 8..128, "Backup passphrase must be 8 to 128 characters")
}

private fun requireBackup(condition: Boolean, message: String) {
    if (!condition) throw BackupException(message)
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

private fun copyBounded(
    input: InputStream,
    output: OutputStream,
    limit: Long,
    digest: MessageDigest
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        copied += count
        requireBackup(copied <= limit, "Backup entry exceeds its size limit")
        digest.update(buffer, 0, count)
        output.write(buffer, 0, count)
    }
    return copied
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
