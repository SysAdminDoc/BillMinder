package com.sysadmindoc.billminder.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.BufferedReader
import java.io.InputStreamReader

data class BackupData(
    val version: Int = 2,
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

    suspend fun exportCsv(context: Context, uri: Uri, repo: BillRepository) {
        val bills = repo.getAllBillsForExport()
        val payments = repo.getAllPaymentsForExport()
        val billMap = bills.associateBy { it.id }

        val sb = StringBuilder()
        sb.appendLine("Bill Name,Category,Amount,Due Day,Recurrence,Auto-Pay,Payment Date,Payment Amount,Confirmation #")
        payments.forEach { p ->
            val bill = billMap[p.billId]
            val name = bill?.name?.replace(",", ";") ?: "Unknown"
            val cat = bill?.category?.label ?: ""
            val billAmt = bill?.amount ?: 0.0
            val dueDay = bill?.dueDay ?: 0
            val rec = bill?.recurrence?.label ?: ""
            val auto = if (bill?.isAutoPay == true) "Yes" else "No"
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(p.paidAt))
            sb.appendLine("$name,$cat,${"%.2f".format(billAmt)},$dueDay,$rec,$auto,$date,${"%.2f".format(p.amount)},${p.confirmationNumber}")
        }

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
    }
}
