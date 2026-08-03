package com.sysadmindoc.billminder.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.BufferedReader
import java.io.InputStreamReader

data class BackupData(
    val version: Int = 3,
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
        var count = 0
        backup.bills.forEach { bill ->
            repo.insertBill(bill.copy(id = 0))
            count++
        }
        backup.payments.forEach { payment ->
            repo.insertPayment(payment.copy(id = 0))
        }
        return count
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
            billMap[p.billId]?.category?.label ?: "Unknown"
        }.toSortedMap()

        val sb = StringBuilder()
        sb.appendLine("BillMinder Year-End Report - $year")
        sb.appendLine()
        sb.appendLine("Category,Bill Name,Payment Date,Amount,Confirmation #")

        var grandTotal = 0.0
        byCategory.forEach { (category, categoryPayments) ->
            val categoryTotal = categoryPayments.sumOf { it.amount }
            grandTotal += categoryTotal
            sb.appendLine()
            sb.appendLine("${csvCell("$category Total")},,,${formatMoney(categoryTotal)},")
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            categoryPayments.sortedBy { it.paidAt }.forEach { p ->
                val bill = billMap[p.billId]
                val name = bill?.name ?: "Unknown"
                val date = dateFormat.format(java.util.Date(p.paidAt))
                sb.appendLine("${csvCell(category)},${csvCell(name)},$date,${formatMoney(p.amount)},${csvCell(p.confirmationNumber)}")
            }
        }
        sb.appendLine()
        sb.appendLine("Grand Total,,,${formatMoney(grandTotal)},")

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
    }

    suspend fun exportCsv(context: Context, uri: Uri, repo: BillRepository) {
        val bills = repo.getAllBillsForExport()
        val payments = repo.getAllPaymentsForExport()
        val billMap = bills.associateBy { it.id }

        val sb = StringBuilder()
        sb.appendLine("Bill Name,Category,Amount,Due Day,Recurrence,Auto-Pay,Payment Date,Payment Amount,Confirmation #")
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
            sb.appendLine("${csvCell(name)},${csvCell(cat)},${formatMoney(billAmt)},$dueDay,${csvCell(rec)},$auto,$date,${formatMoney(p.amount)},${csvCell(p.confirmationNumber)}")
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
