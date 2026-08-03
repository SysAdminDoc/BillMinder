package com.sysadmindoc.billminder.data

import android.content.Context
import android.net.Uri
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class CsvField(
    val label: String,
    val aliases: List<String>,
    val required: Boolean = false
) {
    NAME("Bill name", listOf("billname", "name", "merchant", "payee", "description"), true),
    AMOUNT("Bill amount", listOf("billamount", "amount"), true),
    DUE_DATE("Due date", listOf("duedate")),
    DUE_DAY("Due day", listOf("dueday")),
    CATEGORY("Category", listOf("category", "type")),
    RECURRENCE("Recurrence", listOf("recurrence", "frequency")),
    AUTO_PAY("Auto-pay", listOf("autopay", "automaticpayment")),
    CURRENCY("Bill currency", listOf("billcurrency", "currency")),
    NOTES("Notes", listOf("notes", "note", "memo")),
    PAYMENT_DATE("Payment date", listOf("paymentdate", "paidat", "paiddate")),
    PAYMENT_AMOUNT("Payment amount", listOf("paymentamount", "paidamount")),
    PAYMENT_CURRENCY("Payment currency", listOf("paymentcurrency")),
    CONFIRMATION("Confirmation", listOf("confirmation", "confirmationnumber"))
}

data class CsvTable(
    val headers: List<String>,
    val rows: List<List<String>>
) {
    fun value(row: List<String>, field: CsvField, mapping: CsvImportMapping): String =
        mapping.column(field)?.let { row.getOrNull(it).orEmpty() }.orEmpty().trim()
}

data class CsvImportMapping(
    val columns: Map<CsvField, Int?>
) {
    fun column(field: CsvField): Int? = columns[field]

    fun isReady(): Boolean =
        column(CsvField.NAME) != null &&
            column(CsvField.AMOUNT) != null &&
            (column(CsvField.DUE_DATE) != null || column(CsvField.DUE_DAY) != null)
}

data class CsvImportResult(
    val billsImported: Int,
    val paymentsImported: Int,
    val rowsSkipped: Int
)

object CsvImport {
    fun parse(text: String): CsvTable = CsvParser.parse(text)

    fun suggestedMapping(headers: List<String>): Map<CsvField, Int?> =
        CsvField.entries.associateWith { field ->
            val normalizedHeaders = headers.map { normalizeHeader(it) }
            val exact = field.aliases.firstNotNullOfOrNull { alias ->
                normalizedHeaders.indexOfFirst { it == normalizeHeader(alias) }
                    .takeIf { it >= 0 }
            }
            exact ?: field.aliases.firstNotNullOfOrNull { alias ->
                val normalizedAlias = normalizeHeader(alias)
                normalizedHeaders.indexOfFirst { it.contains(normalizedAlias) }
                    .takeIf { it >= 0 }
            }
        }

    suspend fun read(context: Context, uri: Uri): CsvTable? {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: return null
        return parse(text)
    }

    private fun normalizeHeader(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
}

object CsvParser {
    fun parse(text: String): CsvTable {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0

        fun finishCell() {
            row += cell.toString()
            cell.clear()
        }

        fun finishRow() {
            finishCell()
            if (row.any { it.isNotBlank() }) rows += row.toList()
            row.clear()
        }

        while (index < text.length) {
            val char = text[index]
            if (quoted) {
                if (char == '"') {
                    if (index + 1 < text.length && text[index + 1] == '"') {
                        cell.append('"')
                        index++
                    } else {
                        quoted = false
                    }
                } else {
                    cell.append(char)
                }
            } else {
                when (char) {
                    '"' -> quoted = true
                    ',' -> finishCell()
                    '\n' -> finishRow()
                    '\r' -> Unit
                    else -> cell.append(char)
                }
            }
            index++
        }
        if (quoted || cell.isNotEmpty() || row.isNotEmpty()) finishRow()
        if (rows.isEmpty()) return CsvTable(emptyList(), emptyList())

        val headers = rows.first().mapIndexed { headerIndex, value ->
            if (headerIndex == 0) value.removePrefix("\uFEFF").trim() else value.trim()
        }
        val dataRows = rows.drop(1).map { values ->
            values.take(headers.size) + List((headers.size - values.size).coerceAtLeast(0)) { "" }
        }
        return CsvTable(headers, dataRows)
    }
}

object CsvValueParser {
    fun amount(raw: String): Double? {
        val cleaned = raw.trim()
            .replace(",", "")
            .replace(Regex("[^0-9+.-]"), "")
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun dateMillis(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "MM/dd/yyyy",
            "M/d/yyyy",
            "dd/MM/yyyy"
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            val formatter = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val position = ParsePosition(0)
            formatter.parse(value, position)?.takeIf { position.index == value.length }?.time
        }
    }

    fun boolean(raw: String): Boolean =
        raw.trim().lowercase(Locale.ROOT) in setOf("true", "yes", "y", "1", "on")

    fun category(raw: String): BillCategory =
        BillCategory.entries.firstOrNull {
            it.name.equals(raw.trim(), ignoreCase = true) ||
                it.label.equals(raw.trim(), ignoreCase = true)
        } ?: BillCategory.OTHER

    fun recurrence(raw: String): Recurrence {
        val normalized = raw.trim().lowercase(Locale.ROOT).replace("-", "")
        return Recurrence.entries.firstOrNull {
            it.name.lowercase(Locale.ROOT).replace("_", "") == normalized ||
                it.label.lowercase(Locale.ROOT).replace("-", "") == normalized
        } ?: Recurrence.MONTHLY
    }

    fun dueDay(rawDate: Long, recurrence: Recurrence): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = rawDate }
        return if (recurrence == Recurrence.WEEKLY || recurrence == Recurrence.BIWEEKLY) {
            calendar.get(Calendar.DAY_OF_WEEK)
        } else {
            calendar.get(Calendar.DAY_OF_MONTH)
        }
    }

    fun month(rawDate: Long): Int = Calendar.getInstance().apply { timeInMillis = rawDate }.get(Calendar.MONTH)

    fun year(rawDate: Long): Int = Calendar.getInstance().apply { timeInMillis = rawDate }.get(Calendar.YEAR)
}
