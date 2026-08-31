package com.sysadmindoc.billminder.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sysadmindoc.billminder.domain.CycleEngine
import java.io.BufferedReader
import java.io.InputStreamReader

data class BackupData(
    val version: Int = 5,
    val exportedAt: Long = System.currentTimeMillis(),
    val bills: List<Bill>,
    val payments: List<Payment>
)

object BackupManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportJson(context: Context, uri: Uri, repo: BillRepository) {
        val bills = repo.getAllBillsForExport()
        val payments = repo.getAllPaymentsForExport()
        val backup = BackupData(bills = bills, payments = payments)
        val json = gson.toJson(backup)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        }
    }

    suspend fun importJson(context: Context, uri: Uri, repo: BillRepository): Int {
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        } ?: return 0

        val backup = gson.fromJson(json, BackupData::class.java)

        // One transaction: a partly written restore would leave payments pointing at bills that
        // never made it in.
        return repo.inTransaction {
            val billIdMap = mutableMapOf<Long, Long>()
            var count = 0
            backup.bills.forEach { bill ->
                val normalized = bill.copy(
                    id = 0,
                    name = MerchantNormalizer.normalize(bill.name),
                    currency = CurrencyCatalog.find(bill.currency).code
                )
                billIdMap[bill.id] = repo.insertBill(normalized)
                count++
            }
            backup.payments.forEach { payment ->
                val newBillId = billIdMap[payment.billId] ?: return@forEach
                repo.insertPayment(
                    payment.copy(
                        id = 0,
                        billId = newBillId,
                        currency = CurrencyCatalog.find(payment.currency).code,
                        cycleKey = payment.cycleKey.ifBlank { CycleEngine.cycleKeyForInstant(payment.dueDate) }
                    )
                )
            }
            count
        }
    }

    suspend fun importCsv(
        context: Context,
        uri: Uri,
        repo: BillRepository,
        mapping: CsvImportMapping
    ): CsvImportResult {
        val table = CsvImport.read(context, uri) ?: return CsvImportResult(0, 0, 0)
        if (!mapping.isReady()) return CsvImportResult(0, 0, table.rows.size)

        var rowsSkipped = 0
        val parsed = table.rows.mapNotNull { row ->
            parseRow(table, row, mapping) ?: run { rowsSkipped++; null }
        }

        // A bill's anchor has to be its earliest known date, or every payment older than the anchor
        // would snap forward onto the same occurrence and all but one would be discarded.
        val anchorByKey = parsed
            .groupBy { it.key }
            .mapValues { (_, rows) -> rows.mapNotNull { it.settledOn }.minOrNull() }

        val billIds = mutableMapOf<String, Long>()
        var billsImported = 0
        var paymentsImported = 0

        return repo.inTransaction {
            parsed.forEach { parsedRow ->
                val billId = billIds[parsedRow.key] ?: repo.insertBill(
                    parsedRow.toBill(anchorByKey[parsedRow.key])
                ).also {
                    billIds[parsedRow.key] = it
                    billsImported++
                }

                val paymentDate = parsedRow.paymentDate
                val paymentAmount = parsedRow.paymentAmount
                if (paymentDate != null && paymentAmount != null && paymentAmount > 0.0) {
                    // Snap onto the bill's own grid so an import cannot invent a cycle.
                    val importedBill = repo.getBillById(billId)
                    val settledDate = parsedRow.settledOn ?: CycleEngine.toLocalDate(paymentDate)
                    val cycleDate = importedBill
                        ?.let { CycleEngine.nearestOccurrence(it, settledDate) }
                        ?: settledDate
                    val inserted = repo.insertPayment(
                        Payment(
                            billId = billId,
                            amount = paymentAmount,
                            paidAt = paymentDate,
                            dueDate = CycleEngine.dueInstant(cycleDate),
                            confirmationNumber = parsedRow.confirmation,
                            currency = parsedRow.paymentCurrency,
                            cycleKey = CycleEngine.cycleKey(cycleDate)
                        )
                    )
                    if (inserted > 0L) paymentsImported++ else rowsSkipped++
                }
            }
            CsvImportResult(billsImported, paymentsImported, rowsSkipped)
        }
    }

    /** One usable CSV row, with everything already parsed and validated. */
    private data class ParsedCsvRow(
        val key: String,
        val name: String,
        val amount: Double,
        val currency: String,
        val recurrence: Recurrence,
        val dueDay: Int,
        val dueMonth: Int?,
        val dueYear: Int?,
        val category: BillCategory,
        val isAutoPay: Boolean,
        val notes: String,
        val settledOn: java.time.LocalDate?,
        val paymentDate: Long?,
        val paymentAmount: Double?,
        val paymentCurrency: String,
        val confirmation: String
    ) {
        fun toBill(anchor: java.time.LocalDate?) = Bill(
            name = name,
            amount = amount,
            dueDay = dueDay,
            dueMonth = dueMonth,
            dueYear = dueYear,
            category = category,
            recurrence = recurrence,
            isAutoPay = isAutoPay,
            notes = notes,
            currency = currency,
            anchorEpochDay = anchor?.toEpochDay() ?: 0L
        )
    }

    private fun parseRow(
        table: CsvTable,
        row: List<String>,
        mapping: CsvImportMapping
    ): ParsedCsvRow? {
        val rawName = table.value(row, CsvField.NAME, mapping)
        val rawRecurrence = table.value(row, CsvField.RECURRENCE, mapping)
        val recurrence = if (rawRecurrence.isBlank()) {
            mapping.defaultRecurrence
        } else {
            CsvValueParser.recurrence(rawRecurrence)
        }
        val dueDate = table.value(row, CsvField.DUE_DATE, mapping)
            .takeIf { it.isNotBlank() }
            ?.let(CsvValueParser::dateMillis)
        val dueDay = table.value(row, CsvField.DUE_DAY, mapping)
            .toIntOrNull()
            ?: dueDate?.let { CsvValueParser.dueDay(it, recurrence) }
        val amount = CsvValueParser.amount(table.value(row, CsvField.AMOUNT, mapping))?.let {
            if (mapping.absoluteAmounts) kotlin.math.abs(it) else it
        }
        val validDueDay = dueDay != null && dueDay in if (
            recurrence == Recurrence.WEEKLY || recurrence == Recurrence.BIWEEKLY
        ) 1..7 else 1..31

        if (rawName.isBlank() || amount == null || amount <= 0.0 || !validDueDay) return null

        val name = MerchantNormalizer.normalize(rawName)
        val currency = CurrencyCatalog.find(table.value(row, CsvField.CURRENCY, mapping)).code
        val dueMonth = if (recurrence == Recurrence.ONE_TIME) dueDate?.let(CsvValueParser::month) else null
        val dueYear = if (recurrence == Recurrence.ONE_TIME) dueDate?.let(CsvValueParser::year) else null
        val paymentDate = table.value(row, CsvField.PAYMENT_DATE, mapping)
            .takeIf { it.isNotBlank() }
            ?.let(CsvValueParser::dateMillis)
        val paymentAmount = table.value(row, CsvField.PAYMENT_AMOUNT, mapping)
            .takeIf { it.isNotBlank() }
            ?.let(CsvValueParser::amount)
            ?.let { if (mapping.absoluteAmounts) kotlin.math.abs(it) else it }
            ?: paymentDate?.let { amount }

        return ParsedCsvRow(
            key = listOf(
                name.lowercase(java.util.Locale.ROOT),
                recurrence.name,
                dueDay,
                dueMonth,
                dueYear,
                currency
            ).joinToString("|"),
            name = name,
            amount = amount,
            currency = currency,
            recurrence = recurrence,
            dueDay = dueDay!!,
            dueMonth = dueMonth,
            dueYear = dueYear,
            category = CsvValueParser.category(table.value(row, CsvField.CATEGORY, mapping)),
            isAutoPay = CsvValueParser.boolean(table.value(row, CsvField.AUTO_PAY, mapping)),
            notes = table.value(row, CsvField.NOTES, mapping),
            // The export the app writes has no due-date column, so a payment date is the only
            // signal of when this bill's schedule actually started.
            settledOn = (dueDate ?: paymentDate)?.let { CycleEngine.toLocalDate(it) },
            paymentDate = paymentDate,
            paymentAmount = paymentAmount,
            paymentCurrency = CurrencyCatalog.find(
                table.value(row, CsvField.PAYMENT_CURRENCY, mapping).ifBlank { currency }
            ).code,
            confirmation = table.value(row, CsvField.CONFIRMATION, mapping)
        )
    }

    suspend fun exportYearEndCsv(context: Context, uri: Uri, repo: BillRepository, year: Int) {
        val bills = repo.getAllBillsForExport()
        val payments = repo.getAllPaymentsForExport()
        val billMap = bills.associateBy { it.id }
        val cal = java.util.Calendar.getInstance()

        // Filter payments to the specified year
        val yearPayments = payments.filter { p ->
            cal.timeInMillis = p.paidAt
            cal.get(java.util.Calendar.YEAR) == year
        }

        // Group by category
        val byCategory = yearPayments.groupBy { p ->
            (billMap[p.billId]?.category?.label ?: "Unknown") to p.currency.ifBlank {
                billMap[p.billId]?.currency ?: "USD"
            }
        }.toList().sortedWith(compareBy({ it.first.first }, { it.first.second }))

        val sb = StringBuilder()
        sb.appendLine("BillMinder Year-End Report ($year)")
        sb.appendLine()
        sb.appendLine("Category,Bill Name,Payment Date,Currency,Amount,Confirmation #")

        byCategory.forEach { (key, categoryPayments) ->
            val category = key.first
            val currency = key.second
            val categoryTotal = categoryPayments.sumOf { it.amount }
            sb.appendLine()
            sb.appendLine("${csvCell("$category Total")},,,${csvCell(currency)},${formatMoney(categoryTotal)},")
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            categoryPayments.sortedBy { it.paidAt }.forEach { p ->
                val bill = billMap[p.billId]
                val name = bill?.name ?: "Unknown"
                val date = dateFormat.format(java.util.Date(p.paidAt))
                sb.appendLine("${csvCell(category)},${csvCell(name)},$date,${csvCell(currency)},${formatMoney(p.amount)},${csvCell(p.confirmationNumber)}")
            }
        }
        yearPayments.groupBy { it.currency.ifBlank { "USD" } }.toSortedMap().forEach { (currency, currencyPayments) ->
            sb.appendLine()
            sb.appendLine("${csvCell("Grand Total")},,,${csvCell(currency)},${formatMoney(currencyPayments.sumOf { it.amount })},")
        }

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
    }

    suspend fun exportCsv(context: Context, uri: Uri, repo: BillRepository) {
        val bills = repo.getAllBillsForExport()
        val payments = repo.getAllPaymentsForExport()
        val billMap = bills.associateBy { it.id }

        val sb = StringBuilder()
        sb.appendLine("Bill Name,Category,Bill Currency,Amount,Due Day,Recurrence,Auto-Pay,Payment Date,Payment Currency,Payment Amount,Confirmation #")
        payments.forEach { p ->
            val bill = billMap[p.billId]
            val name = bill?.name ?: "Unknown"
            val cat = bill?.category?.label ?: ""
            val billAmt = bill?.amount ?: 0.0
            val dueDay = bill?.dueDay ?: 0
            val rec = bill?.recurrence?.label ?: ""
            val auto = if (bill?.isAutoPay == true) "Yes" else "No"
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(p.paidAt))
            sb.appendLine("${csvCell(name)},${csvCell(cat)},${csvCell(bill?.currency ?: "USD")},${formatMoney(billAmt)},$dueDay,${csvCell(rec)},$auto,$date,${csvCell(p.currency.ifBlank { bill?.currency ?: "USD" })},${formatMoney(p.amount)},${csvCell(p.confirmationNumber)}")
        }

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
    }

    suspend fun exportInterchangeCsv(
        context: Context,
        uri: Uri,
        repo: BillRepository,
        format: InterchangeFormat,
        targetCurrency: String,
        manualRates: Map<String, Double>
    ) {
        val csv = InterchangeExporter.export(
            format = format,
            bills = repo.getAllBillsForExport(),
            payments = repo.getAllPaymentsForExport(),
            targetCurrency = targetCurrency,
            convert = { amount, from, to ->
                CurrencyConverter.convert(amount, from, to, manualRates)
            }
        )
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(csv.toByteArray(Charsets.UTF_8))
        }
    }

    private fun formatMoney(value: Double): String =
        String.format(java.util.Locale.US, "%.2f", value)

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
