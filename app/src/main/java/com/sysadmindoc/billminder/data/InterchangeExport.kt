package com.sysadmindoc.billminder.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class InterchangeFormat(
    val label: String,
    val fileName: String,
    val description: String
) {
    BLUECOINS(
        "Bluecoins",
        "billminder_bluecoins.csv",
        "Advanced transaction template with currency and conversion columns"
    ),
    YNAB(
        "YNAB",
        "billminder_ynab.csv",
        "Date, Payee, Category, Memo, Outflow, Inflow"
    ),
    ACTUAL(
        "Actual Budget",
        "billminder_actual.csv",
        "Date, Payee, Notes, Category, Amount"
    )
}

object InterchangeExporter {
    fun export(
        format: InterchangeFormat,
        bills: List<Bill>,
        payments: List<Payment>,
        targetCurrency: String = "USD",
        convert: (Double, String, String) -> Double = { amount, from, to ->
            CurrencyConverter.convert(amount, from, to)
        }
    ): String {
        val billMap = bills.associateBy { it.id }
        val rows = payments.sortedBy { it.paidAt }.mapNotNull { payment ->
            val bill = billMap[payment.billId] ?: return@mapNotNull null
            val nativeCurrency = payment.currency.ifBlank { bill.currency }
            val amount = convert(payment.amount, nativeCurrency, targetCurrency)
            val memo = buildMemo(payment, nativeCurrency, targetCurrency)
            when (format) {
                InterchangeFormat.BLUECOINS -> bluecoinsRow(bill, payment, nativeCurrency, memo)
                InterchangeFormat.YNAB -> ynabRow(bill, payment, amount, memo)
                InterchangeFormat.ACTUAL -> actualRow(bill, payment, amount, memo)
            }
        }
        return buildString {
            appendLine(
                when (format) {
                    InterchangeFormat.BLUECOINS -> "Transaction type,Date,Item Name,Amount,Parent Category,Category,Account Type,Account,Notes,Label,Status,Split,Currency,Conversion Rate"
                    InterchangeFormat.YNAB -> "Date,Payee,Category,Memo,Outflow,Inflow"
                    InterchangeFormat.ACTUAL -> "Date,Payee,Notes,Category,Amount"
                }
            )
            rows.forEach(::appendLine)
        }
    }

    private fun bluecoinsRow(
        bill: Bill,
        payment: Payment,
        currency: String,
        memo: String
    ): String = listOf(
        "e",
        date(payment.paidAt, "M/d/yyyy HH:mm"),
        bill.name,
        money(payment.amount),
        bill.category.label,
        bill.category.label,
        "Bank",
        "BillMinder",
        memo,
        bill.tags,
        "C",
        "",
        currency,
        "1.0"
    ).joinToString(",", transform = ::csvCell)

    private fun ynabRow(
        bill: Bill,
        payment: Payment,
        amount: Double,
        memo: String
    ): String = listOf(
        date(payment.paidAt, "yyyy-MM-dd"),
        bill.name,
        bill.category.label,
        memo,
        money(amount),
        ""
    ).joinToString(",", transform = ::csvCell)

    private fun actualRow(
        bill: Bill,
        payment: Payment,
        amount: Double,
        memo: String
    ): String = listOf(
        date(payment.paidAt, "yyyy-MM-dd"),
        bill.name,
        memo,
        bill.category.label,
        money(-kotlin.math.abs(amount))
    ).joinToString(",", transform = ::csvCell)

    private fun buildMemo(payment: Payment, nativeCurrency: String, targetCurrency: String): String = buildString {
        if (payment.note.isNotBlank()) append(payment.note)
        if (payment.confirmationNumber.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append("Confirmation: ${payment.confirmationNumber}")
        }
        if (nativeCurrency != targetCurrency) {
            if (isNotEmpty()) append(" · ")
            append("Native currency: $nativeCurrency")
        }
    }

    private fun date(millis: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).format(Date(millis))

    private fun money(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
