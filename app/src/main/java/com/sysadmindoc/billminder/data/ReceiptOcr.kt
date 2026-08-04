package com.sysadmindoc.billminder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.Month
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ReceiptOcrResult(
    val amount: Double?,
    val date: LocalDate?
)

object ReceiptOcrParser {
    private val amountLabel = Regex(
        "(?i)\\b(?:total|amount(?:\\s+(?:paid|due))?|balance(?:\\s+due)?|payment|paid)\\b"
    )
    private val currencyAmount = Regex(
        "(?i)(?:[$€£]\\s*|(?:USD|EUR|GBP)\\s*)([0-9]{1,3}(?:,[0-9]{3})*(?:[.,][0-9]{2})?|[0-9]+(?:[.,][0-9]{2})?)"
    )
    private val decimalAmount = Regex(
        "(?<![\\d/])([0-9]{1,3}(?:,[0-9]{3})*\\.[0-9]{2})(?![\\d/])"
    )
    private val isoDate = Regex("(?<!\\d)(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})(?!\\d)")
    private val numericDate = Regex("(?<!\\d)(\\d{1,2})[-/](\\d{1,2})[-/](\\d{2,4})(?!\\d)")
    private val monthDate = Regex(
        "(?i)\\b(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\\s+(\\d{1,2})(?:(?:,\\s*|\\s+)(\\d{4}))?\\b"
    )

    fun parse(text: String, today: LocalDate = LocalDate.now()): ReceiptOcrResult =
        ReceiptOcrResult(
            amount = findAmount(text),
            date = findDate(text, today)
        )

    private fun findAmount(text: String): Double? {
        text.lineSequence()
            .filter { amountLabel.containsMatchIn(it) }
            .mapNotNull(::amountOnLine)
            .firstOrNull()
            ?.let { return it }

        currencyAmount.findAll(text).lastOrNull()?.let {
            return parseAmount(it.groupValues[1])
        }
        return decimalAmount.findAll(text).lastOrNull()?.let { parseAmount(it.groupValues[1]) }
    }

    private fun amountOnLine(line: String): Double? {
        currencyAmount.findAll(line).lastOrNull()?.let {
            return parseAmount(it.groupValues[1])
        }
        return decimalAmount.findAll(line).lastOrNull()?.let { parseAmount(it.groupValues[1]) }
    }

    private fun parseAmount(value: String): Double? {
        val compact = value.replace(" ", "")
        val normalized = if (!compact.contains('.') && compact.count { it == ',' } == 1 &&
            compact.substringAfter(',').length == 2
        ) {
            compact.replace(',', '.')
        } else {
            compact.replace(",", "")
        }
        return normalized.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    private fun findDate(text: String, today: LocalDate): LocalDate? {
        isoDate.find(text)?.let {
            return safeDate(
                year = it.groupValues[1].toInt(),
                month = it.groupValues[2].toInt(),
                day = it.groupValues[3].toInt()
            )
        }
        numericDate.find(text)?.let {
            val rawYear = it.groupValues[3].toInt()
            val year = if (rawYear < 100) 2000 + rawYear else rawYear
            return safeDate(
                year = year,
                month = it.groupValues[1].toInt(),
                day = it.groupValues[2].toInt()
            )
        }
        monthDate.find(text)?.let {
            val month = monthNumber(it.groupValues[1]) ?: return@let
            val year = it.groupValues[3].toIntOrNull() ?: today.year
            val parsed = safeDate(year, month, it.groupValues[2].toInt()) ?: return@let
            return if (it.groupValues[3].isBlank() && parsed.isAfter(today)) {
                parsed.minusYears(1)
            } else {
                parsed
            }
        }
        return null
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (_: RuntimeException) {
            null
        }

    private fun monthNumber(value: String): Int? {
        val normalized = value.lowercase(Locale.US).take(3)
        return Month.entries.firstOrNull {
            it.name.lowercase(Locale.US).take(3) == normalized
        }?.value
    }
}

object ReceiptOcr {
    suspend fun extract(
        context: Context,
        file: File,
        mimeType: String,
        today: LocalDate = LocalDate.now()
    ): ReceiptOcrResult? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val input = createInputImage(context, file, mimeType) ?: return@withContext null
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            ReceiptOcrParser.parse(recognizer.process(input.image).awaitText().text, today)
        } finally {
            recognizer.close()
            input.bitmap?.recycle()
        }
    }

    private fun createInputImage(context: Context, file: File, mimeType: String): OcrInput? {
        if (mimeType.startsWith("image/")) {
            return OcrInput(InputImage.fromFilePath(context, Uri.fromFile(file)), null)
        }
        if (mimeType != "application/pdf" && !file.name.endsWith(".pdf", ignoreCase = true)) {
            return null
        }

        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        return try {
            if (renderer.pageCount == 0) return null
            val page = renderer.openPage(0)
            try {
                val scale = minOf(1f, 2000f / maxOf(page.width, page.height).toFloat())
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                OcrInput(InputImage.fromBitmap(bitmap, 0), bitmap)
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    private data class OcrInput(val image: InputImage, val bitmap: Bitmap?)

    private suspend fun Task<com.google.mlkit.vision.text.Text>.awaitText(): com.google.mlkit.vision.text.Text =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
}
