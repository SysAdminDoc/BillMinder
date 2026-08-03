package com.sysadmindoc.billminder.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
        val billIdMap = mutableMapOf<Long, Long>()
        var count = 0
        backup.bills.forEach { bill ->
            val normalized = bill.copy(
                id = 0,
                name = MerchantNormalizer.normalize(bill.name),
                currency = CurrencyCatalog.find(bill.currency).code
            )
            val newId = repo.insertBill(normalized)
            billIdMap[bill.id] = newId
            count++
        }
        backup.payments.forEach { payment ->
            val newBillId = billIdMap[payment.billId] ?: return@forEach
            repo.insertPayment(
                payment.copy(
                    id = 0,
                    billId = newBillId,
                    currency = CurrencyCatalog.find(payment.currency).code
                )
            )
        }
        return count
    }

    suspend fun importCsv(
        context: Context,
        uri: Uri,
        repo: BillRepository,
        mapping: CsvImportMapping
    ): CsvImportResult {
        val table = CsvImport.read(context, uri) ?: return CsvImportResult(0, 0, 0)
        if (!mapping.isReady()) return CsvImportResult(0, 0, table.rows.size)

        val billIds = mutableMapOf<String, Long>()
        var billsImported = 0
        var paymentsImported = 0
        var rowsSkipped = 0

        table.rows.forEach { row ->
            val rawName = table.value(row, CsvField.NAME, mapping)
            val rawAmount = table.value(row, CsvField.AMOUNT, mapping)
            val recurrence = CsvValueParser.recurrence(table.value(row, CsvField.RECURRENCE, mapping))
            val dueDate = table.value(row, CsvField.DUE_DATE, mapping)
                .takeIf { it.isNotBlank() }
                ?.let(CsvValueParser::dateMillis)
            val dueDay = table.value(row, CsvField.DUE_DAY, mapping)
                .toIntOrNull()
                ?: dueDate?.let { CsvValueParser.dueDay(it, recurrence) }
            val amount = CsvValueParser.amount(rawAmount)
            val validDueDay = dueDay != null && dueDay in if (
                recurrence == Recurrence.WEEKLY || recurrence == Recurrence.BIWEEKLY
            ) 1..7 else 1..31

            if (rawName.isBlank() || amount == null || amount <= 0.0 || !validDueDay) {
                rowsSkipped++
                return@forEach
            }

            val name = MerchantNormalizer.normalize(rawName)
            val currency = CurrencyCatalog.find(table.value(row, CsvField.CURRENCY, mapping)).code
            val dueMonth = if (recurrence == Recurrence.ONE_TIME) dueDate?.let(CsvValueParser::month) else null
            val dueYear = if (recurrence == Recurrence.ONE_TIME) dueDate?.let(CsvValueParser::year) else null
            val key = listOf(
                name.lowercase(java.util.Locale.ROOT),
                recurrence.name,
                dueDay,
                dueMonth,
                dueYear,
                currency
            ).joinToString("|")
            val billId = billIds[key] ?: repo.insertBill(
                Bill(
                    name = name,
                    amount = amount,
                    dueDay = dueDay,
                    dueMonth = dueMonth,
                    dueYear = dueYear,
                    category = CsvValueParser.category(table.value(row, CsvField.CATEGORY, mapping)),
                    recurrence = recurrence,
                    isAutoPay = CsvValueParser.boolean(table.value(row, CsvField.AUTO_PAY, mapping)),
                    notes = table.value(row, CsvField.NOTES, mapping),
                    currency = currency
                )
            ).also {
                billIds[key] = it
                billsImported++
            }

            val paymentDate = table.value(row, CsvField.PAYMENT_DATE, mapping)
                .takeIf { it.isNotBlank() }
                ?.let(CsvValueParser::dateMillis)
            val paymentAmount = table.value(row, CsvField.PAYMENT_AMOUNT, mapping)
                .takeIf { it.isNotBlank() }
                ?.let(CsvValueParser::amount)
                ?: paymentDate?.let { amount }
            if (paymentDate != null && paymentAmount != null && paymentAmount > 0.0) {
                repo.insertPayment(
                    Payment(
                        billId = billId,
                        amount = paymentAmount,
                        paidAt = paymentDate,
                        dueDate = dueDate ?: paymentDate,
                        confirmationNumber = table.value(row, CsvField.CONFIRMATION, mapping),
                        currency = CurrencyCatalog.find(
                            table.value(row, CsvField.PAYMENT_CURRENCY, mapping).ifBlank { currency }
                        ).code
                    )
                )
                paymentsImported++
            }
        }
        return CsvImportResult(billsImported, paymentsImported, rowsSkipped)
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
        sb.appendLine("BillMinder Year-End Report - $year")
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
