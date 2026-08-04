package com.sysadmindoc.billminder.data

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class SmsBillCandidate(
    val name: String,
    val amount: Double,
    val dueDate: LocalDate,
    val currency: String,
    val sender: String,
    val preview: String
)

object SmsBillParser {
    private val dueSignal = Regex(
        "(?i)\\b(?:bill|payment|invoice|amount|balance)\\b.{0,45}\\b(?:due|pay by|deadline)\\b|\\b(?:due|pay by|deadline)\\b"
    )
    private val amountPattern = Regex(
        "(?i)(?:([$€£])\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)|\\b(USD|EUR|GBP)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)|\\b([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(USD|EUR|GBP))"
    )
    private val numericDatePattern = Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b")
    private val monthDatePattern = Regex(
        "(?i)\\b(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\\s+(\\d{1,2})(?:,?\\s+(\\d{4}))?\\b"
    )
    private val monthNumbers = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3, "apr" to 4, "april" to 4,
        "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10, "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    fun parse(sender: String, body: String, today: LocalDate = LocalDate.now()): SmsBillCandidate? {
        val normalized = body.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank() || !dueSignal.containsMatchIn(normalized)) return null

        val amountMatch = amountPattern.find(normalized) ?: return null
        val amountText = amountMatch.groups[2]?.value
            ?: amountMatch.groups[4]?.value
            ?: amountMatch.groups[5]?.value
            ?: return null
        val amount = amountText.replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        val currency = when {
            amountMatch.groups[1]?.value == "$" -> "USD"
            amountMatch.groups[1]?.value == "€" -> "EUR"
            amountMatch.groups[1]?.value == "£" -> "GBP"
            else -> (amountMatch.groups[3]?.value ?: amountMatch.groups[6]?.value)?.uppercase(Locale.ROOT) ?: "USD"
        }
        val dueDate = parseMonthDate(normalized, today) ?: parseNumericDate(normalized, today) ?: return null
        val safeSender = sender.trim().ifBlank { "SMS bill" }
        val displayName = if (safeSender.any { it.isLetter() }) {
            safeSender.take(60)
        } else {
            Regex("(?i)\\b(?:from|at|for)\\s+([A-Za-z][A-Za-z0-9& .'-]{2,50})")
                .find(normalized)?.groupValues?.getOrNull(1)?.trim()
                ?: "SMS bill"
        }
        return SmsBillCandidate(
            name = displayName,
            amount = amount,
            dueDate = dueDate,
            currency = currency,
            sender = safeSender,
            preview = normalized.take(160)
        )
    }

    private fun parseMonthDate(text: String, today: LocalDate): LocalDate? =
        monthDatePattern.find(text)?.let { match ->
            val month = monthNumbers[match.groupValues[1].lowercase(Locale.ROOT)] ?: return@let null
            val day = match.groupValues[2].toIntOrNull() ?: return@let null
            val year = match.groupValues[3].toIntOrNull() ?: today.year
            safeDate(year, month, day)?.let { date ->
                if (match.groupValues[3].isBlank() && date.isBefore(today)) date.plusYears(1) else date
            }
        }

    private fun parseNumericDate(text: String, today: LocalDate): LocalDate? =
        numericDatePattern.find(text)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return@let null
            val day = match.groupValues[2].toIntOrNull() ?: return@let null
            val rawYear = match.groupValues[3]
            val year = when {
                rawYear.isBlank() -> today.year
                rawYear.length == 2 -> 2000 + rawYear.toInt()
                else -> rawYear.toIntOrNull() ?: return@let null
            }
            safeDate(year, month, day)?.let { date ->
                if (rawYear.isBlank() && date.isBefore(today)) date.plusYears(1) else date
            }
        }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

object SmsBillReader {
    suspend fun readRecent(context: Context, limit: Int = 100): List<SmsBillCandidate> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val today = LocalDate.now()
        val candidates = mutableListOf<SmsBillCandidate>()
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            var read = 0
            while (cursor.moveToNext() && read < limit) {
                read++
                val candidate = SmsBillParser.parse(
                    sender = if (addressIndex >= 0) cursor.getString(addressIndex).orEmpty() else "",
                    body = if (bodyIndex >= 0) cursor.getString(bodyIndex).orEmpty() else "",
                    today = today
                )
                if (candidate != null && candidates.none { it.preview == candidate.preview }) {
                    candidates += candidate
                }
            }
        }
        candidates
    }
}
